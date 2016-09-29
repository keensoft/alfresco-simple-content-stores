/*
 * Copyright 2016 Axel Faust
 *
 * Licensed under the Eclipse Public License (EPL), Version 1.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package de.axelfaust.alfresco.simplecontentstores.repo.store;

import java.util.ArrayList;
import java.util.Map;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.tenant.TenantUtil;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Axel Faust
 */
public class TenantRoutingContentStore extends PropertyRestrictableRoutingContentStore<Void>
{

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantRoutingContentStore.class);

    protected Map<String, ContentStore> storeByTenant;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        super.afterPropertiesSet();

        this.afterPropertiesSet_setupStoreData();
    }

    /**
     * @param storeByTenant
     *            the storeByTenant to set
     */
    public void setStoreByTenant(final Map<String, ContentStore> storeByTenant)
    {
        this.storeByTenant = storeByTenant;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentStore getStore(final String contentUrl, final boolean mustExist)
    {
        ContentStore readStore = null;

        if (!TenantUtil.isCurrentDomainDefault())
        {
            final String currentDomain = TenantUtil.getCurrentDomain();
            if (this.storeByTenant.containsKey(currentDomain))
            {
                readStore = this.storeByTenant.get(currentDomain);

                if (!readStore.isContentUrlSupported(contentUrl) || (mustExist && !readStore.exists(contentUrl)))
                {
                    readStore = null;
                }
            }
        }

        if (readStore == null)
        {
            readStore = super.getStore(contentUrl, mustExist);
        }

        return readStore;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentStore selectWriteStoreFromRoutes(final ContentContext ctx)
    {
        final ContentStore writeStore;
        final String tenant = TenantUtil.getCurrentDomain();
        if (this.storeByTenant.containsKey(tenant))
        {
            LOGGER.debug("Selecting store for tenant {} to write {}", tenant, ctx);
            writeStore = this.storeByTenant.get(tenant);
        }
        else
        {
            LOGGER.debug("No store defined for tenant {} - delegating to super.selectWiteStoreFromRoute", tenant);
            writeStore = super.selectWriteStoreFromRoutes(ctx);
        }

        return writeStore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isRoutable(final ContentContext ctx)
    {
        final boolean isRoutable = !TenantUtil.isCurrentDomainDefault();
        return isRoutable;
    }

    private void afterPropertiesSet_setupStoreData()
    {
        PropertyCheck.mandatory(this, "storeByTenant", this.storeByTenant);

        if (this.allStores == null)
        {
            this.allStores = new ArrayList<>();
        }

        for (final ContentStore store : this.storeByTenant.values())
        {
            if (!this.allStores.contains(store))
            {
                this.allStores.add(store);
            }
        }
    }
}
