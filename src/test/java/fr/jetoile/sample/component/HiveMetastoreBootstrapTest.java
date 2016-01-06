package fr.jetoile.sample.component;


import com.github.sakserv.minicluster.config.ConfigVars;
import fr.jetoile.sample.exception.BootstrapException;
import fr.jetoile.sample.Utils;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient;
import org.apache.hadoop.hive.metastore.TableType;
import org.apache.hadoop.hive.metastore.api.*;
import org.apache.hadoop.hive.ql.io.orc.OrcSerde;
import org.apache.hadoop.hive.serde.serdeConstants;
import org.apache.thrift.TException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;

@Ignore
public class HiveMetastoreBootstrapTest {

    private static Bootstrap hiveMetastore;
    private static Configuration configuration;


    @BeforeClass
    public static void setup() throws BootstrapException {
        hiveMetastore = HiveMetastoreBootstrap.INSTANCE.start();

        try {
            configuration = new PropertiesConfiguration("default.properties");
        } catch (ConfigurationException e) {
            throw new BootstrapException("bad config", e);
        }

    }

    @AfterClass
    public static void tearDown() throws BootstrapException {
        hiveMetastore.stop();
    }

    @Test
    public void hiveMetastoreShouldStart() throws InterruptedException {
        assertThat(Utils.available("127.0.0.1", 20102)).isFalse();


        // Create a table and display it back
        try {
            HiveMetaStoreClient hiveClient = new HiveMetaStoreClient((HiveConf) hiveMetastore.getConfiguration());

            hiveClient.dropTable(configuration.getString(ConfigVars.HIVE_TEST_DATABASE_NAME_KEY),
                    configuration.getString(ConfigVars.HIVE_TEST_TABLE_NAME_KEY),
                    true,
                    true);

            // Define the cols
            List<FieldSchema> cols = new ArrayList<>();
            cols.add(new FieldSchema("id", serdeConstants.INT_TYPE_NAME, ""));
            cols.add(new FieldSchema("msg", serdeConstants.STRING_TYPE_NAME, ""));

            // Values for the StorageDescriptor
            String location = new File(configuration.getString(ConfigVars.HIVE_TEST_TABLE_NAME_KEY)).getAbsolutePath();
            String inputFormat = "org.apache.hadoop.hive.ql.io.orc.OrcInputFormat";
            String outputFormat = "org.apache.hadoop.hive.ql.io.orc.OrcOutputFormat";
            int numBuckets = 16;
            Map<String, String> orcProps = new HashMap<>();
            orcProps.put("orc.compress", "NONE");
            SerDeInfo serDeInfo = new SerDeInfo(OrcSerde.class.getSimpleName(), OrcSerde.class.getName(), orcProps);
            List<String> bucketCols = new ArrayList<>();
            bucketCols.add("id");

            // Build the StorageDescriptor
            StorageDescriptor sd = new StorageDescriptor();
            sd.setCols(cols);
            sd.setLocation(location);
            sd.setInputFormat(inputFormat);
            sd.setOutputFormat(outputFormat);
            sd.setNumBuckets(numBuckets);
            sd.setSerdeInfo(serDeInfo);
            sd.setBucketCols(bucketCols);
            sd.setSortCols(new ArrayList<>());
            sd.setParameters(new HashMap<>());

            // Define the table
            Table tbl = new Table();
            tbl.setDbName(configuration.getString(ConfigVars.HIVE_TEST_DATABASE_NAME_KEY));
            tbl.setTableName(configuration.getString(ConfigVars.HIVE_TEST_TABLE_NAME_KEY));
            tbl.setSd(sd);
            tbl.setOwner(System.getProperty("user.name"));
            tbl.setParameters(new HashMap<>());
            tbl.setViewOriginalText("");
            tbl.setViewExpandedText("");
            tbl.setTableType(TableType.EXTERNAL_TABLE.name());
            List<FieldSchema> partitions = new ArrayList<FieldSchema>();
            partitions.add(new FieldSchema("dt", serdeConstants.STRING_TYPE_NAME, ""));
            tbl.setPartitionKeys(partitions);

            // Create the table
            hiveClient.createTable(tbl);

            // Describe the table
            Table createdTable = hiveClient.getTable(
                    configuration.getString(ConfigVars.HIVE_TEST_DATABASE_NAME_KEY),
                    configuration.getString(ConfigVars.HIVE_TEST_TABLE_NAME_KEY));
            assertThat(createdTable.toString()).contains(configuration.getString(ConfigVars.HIVE_TEST_TABLE_NAME_KEY));

        } catch (MetaException e) {
            e.printStackTrace();
        } catch (TException e) {
            e.printStackTrace();
        }
    }
}