/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gaffer.accumulostore;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import gaffer.accumulostore.inputformat.ElementInputFormat;
import gaffer.accumulostore.key.AccumuloKeyPackage;
import gaffer.accumulostore.key.exception.AccumuloElementConversionException;
import gaffer.accumulostore.key.exception.IteratorSettingException;
import gaffer.accumulostore.operation.handler.AddElementsHandler;
import gaffer.accumulostore.operation.handler.GetAdjacentEntitySeedsHandler;
import gaffer.accumulostore.operation.handler.GetAllElementsHandler;
import gaffer.accumulostore.operation.handler.GetElementsBetweenSetsHandler;
import gaffer.accumulostore.operation.handler.GetElementsHandler;
import gaffer.accumulostore.operation.handler.GetElementsInRangesHandler;
import gaffer.accumulostore.operation.handler.GetElementsWithinSetHandler;
import gaffer.accumulostore.operation.handler.SummariseGroupOverRangesHandler;
import gaffer.accumulostore.operation.hdfs.handler.AddElementsFromHdfsHandler;
import gaffer.accumulostore.operation.hdfs.handler.ImportAccumuloKeyValueFilesHandler;
import gaffer.accumulostore.operation.hdfs.handler.SampleDataForSplitPointsHandler;
import gaffer.accumulostore.operation.hdfs.handler.SplitTableHandler;
import gaffer.accumulostore.operation.hdfs.operation.ImportAccumuloKeyValueFiles;
import gaffer.accumulostore.operation.hdfs.operation.SampleDataForSplitPoints;
import gaffer.accumulostore.operation.hdfs.operation.SplitTable;
import gaffer.accumulostore.operation.impl.GetEdgesBetweenSets;
import gaffer.accumulostore.operation.impl.GetEdgesInRanges;
import gaffer.accumulostore.operation.impl.GetEdgesWithinSet;
import gaffer.accumulostore.operation.impl.GetElementsBetweenSets;
import gaffer.accumulostore.operation.impl.GetElementsInRanges;
import gaffer.accumulostore.operation.impl.GetElementsWithinSet;
import gaffer.accumulostore.operation.impl.GetEntitiesInRanges;
import gaffer.accumulostore.operation.impl.SummariseGroupOverRanges;
import gaffer.accumulostore.operation.spark.handler.dataframe.GetDataFrameOfElementsOperationHandler;
import gaffer.accumulostore.operation.spark.handler.javardd.GetJavaRDDOfAllElementsOperationHandler;
import gaffer.accumulostore.operation.spark.handler.javardd.GetJavaRDDOfElementsOperationHandler;
import gaffer.accumulostore.operation.spark.handler.scalardd.GetRDDOfAllElementsOperationHandler;
import gaffer.accumulostore.operation.spark.handler.scalardd.GetRDDOfElementsOperationHandler;
import gaffer.accumulostore.utils.Pair;
import gaffer.accumulostore.utils.TableUtils;
import gaffer.commonutil.CommonConstants;
import gaffer.commonutil.iterable.CloseableIterable;
import gaffer.data.element.Element;
import gaffer.data.elementdefinition.view.View;
import gaffer.operation.Operation;
import gaffer.operation.data.ElementSeed;
import gaffer.operation.data.EntitySeed;
import gaffer.operation.impl.add.AddElements;
import gaffer.operation.impl.get.GetAdjacentEntitySeeds;
import gaffer.operation.impl.get.GetAllElements;
import gaffer.operation.impl.get.GetElements;
import gaffer.operation.simple.hdfs.operation.AddElementsFromHdfs;
import gaffer.operation.simple.spark.dataframe.GetDataFrameOfElements;
import gaffer.operation.simple.spark.javardd.GetJavaRDDOfAllElements;
import gaffer.operation.simple.spark.javardd.GetJavaRDDOfElements;
import gaffer.operation.simple.spark.scalardd.GetRDDOfAllElements;
import gaffer.operation.simple.spark.scalardd.GetRDDOfElements;
import gaffer.store.Context;
import gaffer.store.Store;
import gaffer.store.StoreException;
import gaffer.store.StoreProperties;
import gaffer.store.StoreTrait;
import gaffer.store.operation.handler.OperationHandler;
import gaffer.store.schema.Schema;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.ClientConfiguration;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.IteratorSetting;
import org.apache.accumulo.core.client.MutationsRejectedException;
import org.apache.accumulo.core.client.mapreduce.AccumuloInputFormat;
import org.apache.accumulo.core.client.mapreduce.lib.impl.InputConfigurator;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.ColumnVisibility;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static gaffer.store.StoreTrait.AGGREGATION;
import static gaffer.store.StoreTrait.ORDERED;
import static gaffer.store.StoreTrait.POST_AGGREGATION_FILTERING;
import static gaffer.store.StoreTrait.POST_TRANSFORMATION_FILTERING;
import static gaffer.store.StoreTrait.PRE_AGGREGATION_FILTERING;
import static gaffer.store.StoreTrait.STORE_VALIDATION;
import static gaffer.store.StoreTrait.TRANSFORMATION;
import static gaffer.store.StoreTrait.VISIBILITY;


/**
 * An Accumulo Implementation of the Gaffer Framework
 * <p>
 * The key detail of the Accumulo implementation is that any Edge inserted by a
 * user is inserted into the accumulo table twice, once with the source object
 * being put first in the key and once with the destination bring put first in
 * the key This is to enable an edge to be found in a Range scan when providing
 * only one end of the edge.
 */
public class AccumuloStore extends Store {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccumuloStore.class);
    private static final Set<StoreTrait> TRAITS = new HashSet<>(Arrays.asList(AGGREGATION, PRE_AGGREGATION_FILTERING, POST_AGGREGATION_FILTERING, POST_TRANSFORMATION_FILTERING, TRANSFORMATION, STORE_VALIDATION, ORDERED, VISIBILITY));
    private AccumuloKeyPackage keyPackage;
    private Connector connection = null;

    @Override
    public void initialise(final Schema schema, final StoreProperties properties)
            throws StoreException {
        super.initialise(schema, properties);
        final String keyPackageClass = getProperties().getKeyPackageClass();
        try {
            this.keyPackage = Class.forName(keyPackageClass).asSubclass(AccumuloKeyPackage.class).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new StoreException("Unable to construct an instance of key package: " + keyPackageClass);
        }
        this.keyPackage.setSchema(schema);
        TableUtils.ensureTableExists(this);
    }

    /**
     * Creates an Accumulo {@link org.apache.accumulo.core.client.Connector}
     * using the properties found in properties file associated with the
     * AccumuloStore
     *
     * @return A new {@link Connector}
     * @throws StoreException if there is a failure to connect to accumulo.
     */
    public Connector getConnection() throws StoreException {
        if (null == connection) {
            connection = TableUtils.getConnector(getProperties().getInstanceName(), getProperties().getZookeepers(),
                    getProperties().getUserName(), getProperties().getPassword());
        }
        return connection;
    }

    /**
     * Updates a Hadoop {@link Configuration} with information needed to connect to the Accumulo store. It adds
     * iterators to apply the provided {@link View}. This method will be used by operations that run MapReduce
     * or Spark jobs against the Accumulo store.
     *
     * @param conf A {@link Configuration} to be updated.
     * @param view The {@link View} to be applied.
     * @throws StoreException if there is a failure to connect to Accumulo or a problem setting the iterators.
     */
    public void updateConfiguration(final Configuration conf, final View view) throws StoreException {
        try {
            // Table name
            InputConfigurator.setInputTableName(AccumuloInputFormat.class,
                    conf,
                    getProperties().getTable());
            // User
            addUserToConfiguration(conf);
            // Authorizations
            InputConfigurator.setScanAuthorizations(AccumuloInputFormat.class,
                    conf,
                    TableUtils.getCurrentAuthorizations(getConnection()));
            // Zookeeper
            addZookeeperToConfiguration(conf);
            // Add keypackage, schema and view to conf
            conf.set(ElementInputFormat.KEY_PACKAGE, getProperties().getKeyPackageClass());
            conf.set(ElementInputFormat.SCHEMA, new String(getSchema().toJson(false), CommonConstants.UTF_8));
            conf.set(ElementInputFormat.VIEW, new String(view.toJson(false), CommonConstants.UTF_8));
            // Add iterators that depend on the view
            if (!view.getEntityGroups().isEmpty() || !view.getEdgeGroups().isEmpty()) {
                IteratorSetting elementPreFilter = getKeyPackage()
                        .getIteratorFactory()
                        .getElementPreAggregationFilterIteratorSetting(view, this);
                IteratorSetting elementPostFilter = getKeyPackage()
                        .getIteratorFactory()
                        .getElementPostAggregationFilterIteratorSetting(view, this);
                InputConfigurator.addIterator(AccumuloInputFormat.class, conf, elementPostFilter);
                InputConfigurator.addIterator(AccumuloInputFormat.class, conf, elementPreFilter);
            }

        } catch (final AccumuloSecurityException | IteratorSettingException | UnsupportedEncodingException e) {
            throw new StoreException(e);
        }
    }

    protected void addUserToConfiguration(final Configuration conf) throws AccumuloSecurityException {
        InputConfigurator.setConnectorInfo(AccumuloInputFormat.class,
                conf,
                getProperties().getUserName(),
                new PasswordToken(getProperties().getPassword()));
    }

    protected void addZookeeperToConfiguration(final Configuration conf) {
        InputConfigurator.setZooKeeperInstance(AccumuloInputFormat.class,
                conf,
                new ClientConfiguration()
                        .withInstance(getProperties().getInstanceName())
                        .withZkHosts(getProperties().getZookeepers()));
    }

    @Override
    public <OUTPUT> OUTPUT doUnhandledOperation(final Operation<?, OUTPUT> operation, final Context context) {
        throw new UnsupportedOperationException("Operation: " + operation.getClass() + " is not supported");
    }

    @SuppressFBWarnings(value = "BC_UNCONFIRMED_CAST_OF_RETURN_VALUE", justification = "The properties should always be AccumuloProperties")
    @Override
    public AccumuloProperties getProperties() {
        return (AccumuloProperties) super.getProperties();
    }

    @Override
    protected void addAdditionalOperationHandlers() {
        addOperationHandler(AddElementsFromHdfs.class, new AddElementsFromHdfsHandler());
        addOperationHandler(GetEdgesBetweenSets.class, new GetElementsBetweenSetsHandler());
        addOperationHandler(GetElementsBetweenSets.class, new GetElementsBetweenSetsHandler());
        addOperationHandler(GetEdgesInRanges.class, new GetElementsInRangesHandler());
        addOperationHandler(GetElementsInRanges.class, new GetElementsInRangesHandler());
        addOperationHandler(GetEntitiesInRanges.class, new GetElementsInRangesHandler());
        addOperationHandler(GetElementsWithinSet.class, new GetElementsWithinSetHandler());
        addOperationHandler(GetEdgesWithinSet.class, new GetElementsWithinSetHandler());
        addOperationHandler(SplitTable.class, new SplitTableHandler());
        addOperationHandler(SampleDataForSplitPoints.class, new SampleDataForSplitPointsHandler());
        addOperationHandler(ImportAccumuloKeyValueFiles.class, new ImportAccumuloKeyValueFilesHandler());
        addOperationHandler(SummariseGroupOverRanges.class, new SummariseGroupOverRangesHandler());
        addOperationHandler(GetJavaRDDOfElements.class, new GetJavaRDDOfElementsOperationHandler());
        addOperationHandler(GetRDDOfElements.class, new GetRDDOfElementsOperationHandler());
        addOperationHandler(GetRDDOfAllElements.class, new GetRDDOfAllElementsOperationHandler());
        addOperationHandler(GetJavaRDDOfAllElements.class, new GetJavaRDDOfAllElementsOperationHandler());
        addOperationHandler(GetDataFrameOfElements.class, new GetDataFrameOfElementsOperationHandler());
    }

    @Override
    protected OperationHandler<GetElements<ElementSeed, Element>, CloseableIterable<Element>> getGetElementsHandler() {
        return new GetElementsHandler();
    }

    @Override
    protected OperationHandler<GetAllElements<Element>, CloseableIterable<Element>> getGetAllElementsHandler() {
        return new GetAllElementsHandler();
    }

    @Override
    protected OperationHandler<? extends GetAdjacentEntitySeeds, CloseableIterable<EntitySeed>> getAdjacentEntitySeedsHandler() {
        return new GetAdjacentEntitySeedsHandler();
    }

    @Override
    protected OperationHandler<? extends AddElements, Void> getAddElementsHandler() {
        return new AddElementsHandler();
    }

    @Override
    public Set<StoreTrait> getTraits() {
        return TRAITS;
    }

    /**
     * Method to add {@link Element}s into Accumulo
     *
     * @param elements the elements to be added
     * @throws StoreException failure to insert the elements into a table
     */
    public void addElements(final Iterable<Element> elements) throws StoreException {
        insertGraphElements(elements);
    }

    protected void insertGraphElements(final Iterable<Element> elements) throws StoreException {
        // Create BatchWriter
        final BatchWriter writer = TableUtils.createBatchWriter(this);
        // Loop through elements, convert to mutations, and add to
        // BatchWriter.as
        // The BatchWriter takes care of batching them up, sending them without
        // too high a latency, etc.
        for (final Element element : elements) {
            final Pair<Key> keys;
            try {
                keys = keyPackage.getKeyConverter().getKeysFromElement(element);
            } catch (final AccumuloElementConversionException e) {
                LOGGER.error("Failed to create an accumulo key from element of type " + element.getGroup()
                        + " when trying to insert elements");
                continue;
            }
            final Value value;
            try {
                value = keyPackage.getKeyConverter().getValueFromElement(element);
            } catch (final AccumuloElementConversionException e) {
                LOGGER.error("Failed to create an accumulo value from element of type " + element.getGroup()
                        + " when trying to insert elements");
                continue;
            }
            final Mutation m = new Mutation(keys.getFirst().getRow());
            m.put(keys.getFirst().getColumnFamily(), keys.getFirst().getColumnQualifier(),
                    new ColumnVisibility(keys.getFirst().getColumnVisibility()), keys.getFirst().getTimestamp(), value);
            try {
                writer.addMutation(m);
            } catch (final MutationsRejectedException e) {
                LOGGER.error("Failed to create an accumulo key mutation");
                continue;
            }
            // If the GraphElement is a Vertex then there will only be 1 key,
            // and the second will be null.
            // If the GraphElement is an Edge then there will be 2 keys.
            if (keys.getSecond() != null) {
                final Mutation m2 = new Mutation(keys.getSecond().getRow());
                m2.put(keys.getSecond().getColumnFamily(), keys.getSecond().getColumnQualifier(),
                        new ColumnVisibility(keys.getSecond().getColumnVisibility()), keys.getSecond().getTimestamp(),
                        value);
                try {
                    writer.addMutation(m2);
                } catch (final MutationsRejectedException e) {
                    LOGGER.error("Failed to create an accumulo key mutation");
                }
            }
        }
        try {
            writer.close();
        } catch (final MutationsRejectedException e) {
            LOGGER.warn("Accumulo batch writer failed to close", e);
        }
    }

    /**
     * Returns the {@link gaffer.accumulostore.key.AccumuloKeyPackage} in use by
     * this AccumuloStore.
     *
     * @return {@link gaffer.accumulostore.key.AccumuloKeyPackage}
     */
    public AccumuloKeyPackage getKeyPackage() {
        return keyPackage;
    }

    @Override
    public boolean isValidationRequired() {
        return false;
    }
}
