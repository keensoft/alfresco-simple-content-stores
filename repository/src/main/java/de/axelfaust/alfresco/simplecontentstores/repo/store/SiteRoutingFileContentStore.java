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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentLimitProvider;
import org.alfresco.repo.content.ContentLimitProvider.NoLimitProvider;
import org.alfresco.repo.content.ContentLimitProvider.SimpleFixedLimitProvider;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.copy.CopyServicePolicies.OnCopyCompletePolicy;
import org.alfresco.repo.node.NodeServicePolicies.OnMoveNodePolicy;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.axelfaust.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;
import de.axelfaust.alfresco.simplecontentstores.repo.store.context.ContentStoreContext.ContentStoreOperation;

/**
 * @author Axel Faust
 */
public class SiteRoutingFileContentStore extends MoveCapableCommonRoutingContentStore<Void>
        implements OnCopyCompletePolicy, OnMoveNodePolicy
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SiteRoutingFileContentStore.class);

    protected NamespaceService namespaceService;

    protected String rootAbsolutePath;

    protected Map<String, String> rootAbsolutePathsBySitePreset;

    protected Map<String, String> rootAbsolutePathsBySite;

    protected String protocol = FileContentStore.STORE_PROTOCOL;

    protected Map<String, String> protocolsBySitePreset;

    protected Map<String, String> protocolsBySite;

    protected transient Map<String, ContentStore> storeByProtocol = new HashMap<>();

    protected boolean allowRandomAccess;

    protected boolean readOnly;

    protected boolean deleteEmptyDirs = true;

    protected boolean useSiteFolderInGenericDirectories;

    protected boolean moveStoresOnNodeMoveOrCopy;

    protected ContentLimitProvider contentLimitProvider = new NoLimitProvider();

    protected Map<String, ContentLimitProvider> contentLimitProviderBySitePreset;

    protected Map<String, ContentLimitProvider> contentLimitProviderBySite;

    protected String moveStoresOnNodeMoveOrCopyName;

    protected transient QName moveStoresOnNodeMoveOrCopyQName;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "namespaceService", this.namespaceService);

        this.afterPropertiesSet_setupDefaultStore();

        super.afterPropertiesSet();

        this.afterPropertiesSet_setupStoreData();
        this.afterPropertiesSet_setupChangePolicies();
    }

    /**
     * @param namespaceService
     *            the namespaceService to set
     */
    public void setNamespaceService(final NamespaceService namespaceService)
    {
        this.namespaceService = namespaceService;
    }

    /**
     * @param rootAbsolutePath
     *            the rootAbsolutePath to set
     */
    public void setRootAbsolutePath(final String rootAbsolutePath)
    {
        this.rootAbsolutePath = rootAbsolutePath;
    }

    /**
     * Simple alias to {@ink #setRootAbsolutePath(String)} for compatibility with previously used {@code FileContentStoreFactoryBean}
     *
     * @param rootDirectory
     *            the rootDirectory to set
     */
    public void setRootDirectory(final String rootDirectory)
    {
        this.rootAbsolutePath = rootDirectory;
    }

    /**
     * @param rootAbsolutePathsBySitePreset
     *            the rootAbsolutePathsBySitePreset to set
     */
    public void setRootAbsolutePathsBySitePreset(final Map<String, String> rootAbsolutePathsBySitePreset)
    {
        this.rootAbsolutePathsBySitePreset = rootAbsolutePathsBySitePreset;
    }

    /**
     * @param rootAbsolutePathsBySite
     *            the rootAbsolutePathsBySite to set
     */
    public void setRootAbsolutePathsBySite(final Map<String, String> rootAbsolutePathsBySite)
    {
        this.rootAbsolutePathsBySite = rootAbsolutePathsBySite;
    }

    /**
     * @param protocol
     *            the protocol to set
     */
    public void setProtocol(final String protocol)
    {
        this.protocol = protocol;
    }

    /**
     * @param protocolsBySitePreset
     *            the protocolsBySitePreset to set
     */
    public void setProtocolsBySitePreset(final Map<String, String> protocolsBySitePreset)
    {
        this.protocolsBySitePreset = protocolsBySitePreset;
    }

    /**
     * @param protocolsBySite
     *            the protocolsBySite to set
     */
    public void setProtocolsBySite(final Map<String, String> protocolsBySite)
    {
        this.protocolsBySite = protocolsBySite;
    }

    /**
     * @param allowRandomAccess
     *            the allowRandomAccess to set
     */
    public void setAllowRandomAccess(final boolean allowRandomAccess)
    {
        this.allowRandomAccess = allowRandomAccess;
    }

    /**
     * @param readOnly
     *            the readOnly to set
     */
    public void setReadOnly(final boolean readOnly)
    {
        this.readOnly = readOnly;
    }

    /**
     * @param deleteEmptyDirs
     *            the deleteEmptyDirs to set
     */
    public void setDeleteEmptyDirs(final boolean deleteEmptyDirs)
    {
        this.deleteEmptyDirs = deleteEmptyDirs;
    }

    /**
     * @param useSiteFolderInGenericDirectories
     *            the useSiteFolderInGenericDirectories to set
     */
    public void setUseSiteFolderInGenericDirectories(final boolean useSiteFolderInGenericDirectories)
    {
        this.useSiteFolderInGenericDirectories = useSiteFolderInGenericDirectories;
    }

    /**
     * An object that prevents abuse of the underlying store(s)
     */
    public void setContentLimitProvider(final ContentLimitProvider contentLimitProvider)
    {
        this.contentLimitProvider = contentLimitProvider;
    }

    /**
     *
     * @param limit
     *            the fixed content limit to set
     */
    public void setFixedLimit(final long limit)
    {
        if (limit < 0 && limit != ContentLimitProvider.NO_LIMIT)
        {
            throw new IllegalArgumentException("fixedLimit must be non-negative");
        }
        this.setContentLimitProvider(new SimpleFixedLimitProvider(limit));
    }

    /**
     * @param contentLimitProviderBySitePreset
     *            the contentLimitProviderBySitePreset to set
     */
    public void setContentLimitProviderBySitePreset(final Map<String, ContentLimitProvider> contentLimitProviderBySitePreset)
    {
        this.contentLimitProviderBySitePreset = contentLimitProviderBySitePreset;
    }

    /**
     *
     * @param limits
     *            the fixed limits to set
     */
    public void setFixedLimitBySitePreset(final Map<String, Long> limits)
    {
        ParameterCheck.mandatory("limits", limits);

        if (this.contentLimitProviderBySitePreset == null)
        {
            this.contentLimitProviderBySitePreset = new HashMap<>();
        }

        for (final Entry<String, Long> limitEntry : limits.entrySet())
        {
            final long limit = limitEntry.getValue().longValue();
            if (limit < 0 && limit != ContentLimitProvider.NO_LIMIT)
            {
                throw new IllegalArgumentException("fixedLimit must be non-negative");
            }
            this.contentLimitProviderBySitePreset.put(limitEntry.getKey(), new SimpleFixedLimitProvider(limit));
        }
    }

    /**
     * @param contentLimitProviderBySite
     *            the contentLimitProviderBySite to set
     */
    public void setContentLimitProviderBySite(final Map<String, ContentLimitProvider> contentLimitProviderBySite)
    {
        this.contentLimitProviderBySite = contentLimitProviderBySite;
    }

    /**
     *
     * @param limits
     *            the fixed limits to set
     */
    public void setFixedLimitBySite(final Map<String, Long> limits)
    {
        ParameterCheck.mandatory("limits", limits);

        if (this.contentLimitProviderBySite == null)
        {
            this.contentLimitProviderBySite = new HashMap<>();
        }

        for (final Entry<String, Long> limitEntry : limits.entrySet())
        {
            final long limit = limitEntry.getValue().longValue();
            if (limit < 0 && limit != ContentLimitProvider.NO_LIMIT)
            {
                throw new IllegalArgumentException("fixedLimit must be non-negative");
            }
            this.contentLimitProviderBySite.put(limitEntry.getKey(), new SimpleFixedLimitProvider(limit));
        }
    }

    /**
     * @param moveStoresOnNodeMoveOrCopy
     *            the moveStoresOnNodeMoveOrCopy to set
     */
    public void setMoveStoresOnNodeMoveOrCopy(final boolean moveStoresOnNodeMoveOrCopy)
    {
        this.moveStoresOnNodeMoveOrCopy = moveStoresOnNodeMoveOrCopy;
    }

    /**
     * @param moveStoresOnNodeMoveOrCopyName
     *            the moveStoresOnNodeMoveOrCopyName to set
     */
    public void setMoveStoresOnNodeMoveOrCopyName(final String moveStoresOnNodeMoveOrCopyName)
    {
        this.moveStoresOnNodeMoveOrCopyName = moveStoresOnNodeMoveOrCopyName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMoveNode(final ChildAssociationRef oldChildAssocRef, final ChildAssociationRef newChildAssocRef)
    {
        // only act on active nodes which can actually be in a site
        final NodeRef movedNode = oldChildAssocRef.getChildRef();
        if (StoreRef.STORE_REF_WORKSPACE_SPACESSTORE.equals(movedNode.getStoreRef()))
        {
            this.checkAndProcessContentPropertiesMove(movedNode);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCopyComplete(final QName classRef, final NodeRef sourceNodeRef, final NodeRef targetNodeRef, final boolean copyToNewNode,
            final Map<NodeRef, NodeRef> copyMap)
    {
        // only act on active nodes which can actually be in a site
        if (StoreRef.STORE_REF_WORKSPACE_SPACESSTORE.equals(targetNodeRef.getStoreRef()))
        {
            this.checkAndProcessContentPropertiesMove(targetNodeRef);
        }
    }

    protected void checkAndProcessContentPropertiesMove(final NodeRef affectedNode)
    {
        final Collection<QName> contentProperties = this.dictionaryService.getAllProperties(DataTypeDefinition.CONTENT);

        // just copied/moved so properties should be cached
        final Map<QName, Serializable> properties = this.nodeService.getProperties(affectedNode);

        boolean doMove = false;
        if (this.moveStoresOnNodeMoveOrCopyQName != null)
        {
            final Serializable moveStoresOnChangeOptionValue = properties.get(this.moveStoresOnNodeMoveOrCopyQName);
            // explicit value wins
            if (moveStoresOnChangeOptionValue != null)
            {
                doMove = Boolean.TRUE.equals(moveStoresOnChangeOptionValue);
            }
            else
            {
                doMove = this.moveStoresOnNodeMoveOrCopy;
            }
        }
        else
        {
            doMove = this.moveStoresOnNodeMoveOrCopy;
        }

        if (doMove)
        {
            final Collection<QName> setProperties = new HashSet<>(properties.keySet());
            setProperties.retainAll(contentProperties);

            // only act if node actually has content properties set
            if (!setProperties.isEmpty())
            {
                final Map<QName, Serializable> contentPropertiesMap = new HashMap<>();
                for (final QName contentProperty : setProperties)
                {
                    final Serializable value = properties.get(contentProperty);
                    contentPropertiesMap.put(contentProperty, value);
                }

                if (!contentPropertiesMap.isEmpty())
                {
                    ContentStoreContext.executeInNewContext(new ContentStoreOperation<Void>()
                    {

                        /**
                         * {@inheritDoc}
                         */
                        @Override
                        public Void execute()
                        {
                            SiteRoutingFileContentStore.this.processContentPropertiesMove(affectedNode, contentPropertiesMap, null);
                            return null;
                        }
                    });
                }
            }
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentStore selectStoreForContentDataMove(final NodeRef nodeRef, final QName propertyQName, final ContentData contentData,
            final Void customData)
    {
        final ContentStore writeStore = this.selectStoreForCurrentContext();
        return writeStore;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentStore selectWriteStoreFromRoutes(final ContentContext ctx)
    {
        final ContentStore writeStore = this.selectStoreForCurrentContext();
        return writeStore;
    }

    protected ContentStore selectStoreForCurrentContext()
    {
        final Object site = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE);
        final Object sitePreset = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE_PRESET);

        final String protocol;
        if (this.protocolsBySite != null && this.protocolsBySite.containsKey(site))
        {
            LOGGER.debug("Selecting store for site {}", site);
            protocol = this.protocolsBySite.get(site);
        }
        else if (this.protocolsBySitePreset != null && this.protocolsBySitePreset.containsKey(sitePreset))
        {
            LOGGER.debug("Selecting store for site preset {}", sitePreset);
            protocol = this.protocolsBySitePreset.get(sitePreset);
        }
        else
        {
            LOGGER.debug("Selecting default store");
            protocol = this.protocol;
        }

        final ContentStore targetStore = this.storeByProtocol.get(protocol);
        return targetStore;
    }

    protected void afterPropertiesSet_setupDefaultStore()
    {
        PropertyCheck.mandatory(this, "rootAbsolutePath", this.rootAbsolutePath);
        PropertyCheck.mandatory(this, "protocol", this.protocol);

        if (this.allStores == null)
        {
            this.allStores = new ArrayList<>();
        }

        final SiteAwareFileContentStore defaultFileContentStore = new SiteAwareFileContentStore();
        defaultFileContentStore.setProtocol(this.protocol);
        defaultFileContentStore.setRootAbsolutePath(this.rootAbsolutePath);
        defaultFileContentStore.setApplicationContext(this.applicationContext);
        defaultFileContentStore.setContentLimitProvider(this.contentLimitProvider);
        defaultFileContentStore.setAllowRandomAccess(this.allowRandomAccess);
        defaultFileContentStore.setDeleteEmptyDirs(this.deleteEmptyDirs);
        defaultFileContentStore.setReadOnly(this.readOnly);
        defaultFileContentStore.setUseSiteFolderInGenericDirectories(this.useSiteFolderInGenericDirectories);

        this.storeByProtocol.put(this.protocol, defaultFileContentStore);
        this.allStores.add(defaultFileContentStore);
        this.fallbackStore = defaultFileContentStore;

        defaultFileContentStore.afterPropertiesSet();
    }

    protected void afterPropertiesSet_setupStoreData()
    {
        if (this.rootAbsolutePathsBySite != null)
        {
            PropertyCheck.mandatory(this, "protocolBySite", this.protocolsBySite);

            for (final Entry<String, String> entry : this.rootAbsolutePathsBySite.entrySet())
            {
                final String site = entry.getKey();
                final String protocol = this.protocolsBySite.get(site);
                PropertyCheck.mandatory(this, "protocolBySite." + site, protocol);

                if (this.storeByProtocol.containsKey(protocol))
                {
                    throw new ContentIOException("Failed to set up site aware content store - duplicate protocol: " + protocol, null);
                }

                final SiteAwareFileContentStore siteAwareFileContentStore = new SiteAwareFileContentStore();
                siteAwareFileContentStore.setProtocol(protocol);
                siteAwareFileContentStore.setRootAbsolutePath(entry.getValue());
                siteAwareFileContentStore.setApplicationContext(this.applicationContext);

                ContentLimitProvider contentLimitProvider = null;
                if (this.contentLimitProviderBySite != null)
                {
                    contentLimitProvider = this.contentLimitProviderBySite.get(site);
                }
                if (contentLimitProvider == null)
                {
                    contentLimitProvider = this.contentLimitProvider;
                }
                siteAwareFileContentStore.setContentLimitProvider(contentLimitProvider);

                siteAwareFileContentStore.setAllowRandomAccess(this.allowRandomAccess);
                siteAwareFileContentStore.setDeleteEmptyDirs(this.deleteEmptyDirs);
                siteAwareFileContentStore.setReadOnly(this.readOnly);
                siteAwareFileContentStore.setExtendedEventParameters(Collections.<String, Serializable> singletonMap("Site", site));

                this.storeByProtocol.put(protocol, siteAwareFileContentStore);
                this.allStores.add(siteAwareFileContentStore);

                siteAwareFileContentStore.afterPropertiesSet();
            }
        }

        if (this.rootAbsolutePathsBySitePreset != null)
        {
            PropertyCheck.mandatory(this, "protocolBySitePreset", this.protocolsBySitePreset);

            for (final Entry<String, String> entry : this.rootAbsolutePathsBySitePreset.entrySet())
            {
                final String sitePreset = entry.getKey();
                final String protocol = this.protocolsBySitePreset.get(sitePreset);
                PropertyCheck.mandatory(this, "protocolBySitePreset." + sitePreset, protocol);

                if (this.storeByProtocol.containsKey(protocol))
                {
                    throw new ContentIOException("Failed to set up site aware content store - duplicate protocol: " + protocol, null);
                }

                final SiteAwareFileContentStore siteAwareFileContentStore = new SiteAwareFileContentStore();
                siteAwareFileContentStore.setProtocol(protocol);
                siteAwareFileContentStore.setRootAbsolutePath(entry.getValue());
                siteAwareFileContentStore.setApplicationContext(this.applicationContext);

                ContentLimitProvider contentLimitProvider = null;
                if (this.contentLimitProviderBySitePreset != null)
                {
                    contentLimitProvider = this.contentLimitProviderBySitePreset.get(sitePreset);
                }
                if (contentLimitProvider == null)
                {
                    contentLimitProvider = this.contentLimitProvider;
                }
                siteAwareFileContentStore.setContentLimitProvider(contentLimitProvider);

                siteAwareFileContentStore.setAllowRandomAccess(this.allowRandomAccess);
                siteAwareFileContentStore.setDeleteEmptyDirs(this.deleteEmptyDirs);
                siteAwareFileContentStore.setReadOnly(this.readOnly);
                siteAwareFileContentStore.setUseSiteFolderInGenericDirectories(this.useSiteFolderInGenericDirectories);
                siteAwareFileContentStore
                        .setExtendedEventParameters(Collections.<String, Serializable> singletonMap("SitePreset", sitePreset));

                this.storeByProtocol.put(protocol, siteAwareFileContentStore);
                this.allStores.add(siteAwareFileContentStore);

                siteAwareFileContentStore.afterPropertiesSet();
            }
        }
    }

    protected void afterPropertiesSet_setupChangePolicies()
    {
        if (this.moveStoresOnNodeMoveOrCopyName != null)
        {
            this.moveStoresOnNodeMoveOrCopyQName = QName.resolveToQName(this.namespaceService, this.moveStoresOnNodeMoveOrCopyName);
            PropertyCheck.mandatory(this, "moveStoresOnChangeOptionPropertyQName", this.moveStoresOnNodeMoveOrCopyQName);

            final PropertyDefinition moveStoresOnChangeOptionPropertyDefinition = this.dictionaryService
                    .getProperty(this.moveStoresOnNodeMoveOrCopyQName);
            if (moveStoresOnChangeOptionPropertyDefinition == null
                    || !DataTypeDefinition.BOOLEAN.equals(moveStoresOnChangeOptionPropertyDefinition.getDataType().getName())
                    || moveStoresOnChangeOptionPropertyDefinition.isMultiValued())
            {
                throw new IllegalStateException(
                        this.moveStoresOnNodeMoveOrCopyName + " is not a valid content model property of type single-valued d:boolean");
            }
        }

        if (this.moveStoresOnNodeMoveOrCopy || this.moveStoresOnNodeMoveOrCopyQName != null)
        {
            this.policyComponent.bindClassBehaviour(OnCopyCompletePolicy.QNAME, ContentModel.TYPE_BASE,
                    new JavaBehaviour(this, "onCopyComplete", NotificationFrequency.EVERY_EVENT));
            this.policyComponent.bindClassBehaviour(OnMoveNodePolicy.QNAME, ContentModel.TYPE_BASE,
                    new JavaBehaviour(this, "onMoveNode", NotificationFrequency.EVERY_EVENT));
        }
    }
}
