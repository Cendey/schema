package edu.mit.lab.core;

import com.owlike.genson.GenericType;
import com.owlike.genson.Genson;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.mit.lab.constant.Scheme;
import edu.mit.lab.exception.UnIdentifiedException;
import edu.mit.lab.infts.IRelevance;
import edu.mit.lab.infts.idao.IDAOForMeta;
import edu.mit.lab.meta.Keys;
import edu.mit.lab.meta.Tables;
import edu.mit.lab.skeleton.DAOFactory;
import edu.mit.lab.skeleton.factory.IDAOFactory;
import edu.mit.lab.utils.Toolkit;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.Configuration;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.xml.XmlConfiguration;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.GraphFactory;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.ViewerListener;
import org.graphstream.ui.view.ViewerPipe;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * <p>Title: Kewill Lab Center</p>
 * <p>Description: edu.mit.lab.core.Resolver</p>
 * <p>Copyright: Copyright (c) 2016</p>
 * <p>Company: Kewill Co., Ltd.</p>
 *
 * @author <chao.deng@kewill.com>
 * @version 1.0
 * @since 11/14/2016
 */
public class Resolver {

    private static final Logger logger = LogManager.getLogger(Resolver.class);
    private static final int HEIGHT_THRESHOLD = 3;
    private static final String SCRIPT_FILE_NAME = "clear_data_script.sql";
    private static final String GRAPH_FILE_NAME_PREFIX = "stream_graph_";
    private static final String GRAPH_FILE_NAME_SUFFIX = ".gml";
    private static final String TABLES_TO_JSON_FILE_NAME = "tables.json";
    private static final String FOREIGN_TO_JSON_FILE_NAME = "foreign.json";
    private static final int FIXED_ROW_COUNT = 50;

    private int viewClosedCounter;
    private SortedSet<String> rootNodeIds;
    private List<String> tableIds;
    private StringBuilder script;

    static {
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
        } catch (ClassNotFoundException e) {
            logger.error(e.getMessage());
        }
    }

    private void init() {
        viewClosedCounter = 0;
        tableIds = new LinkedList<>();
        script = new StringBuilder(Scheme.PREVENT_EDITOR_ADD_BOM_HEADER);
    }

    public Resolver() {
        init();
    }

    /**
     * <p>Summary:</p><p>Collect all root node(s) id(s)</p>
     *
     * @param rootNodeIds <p>Sorted root node(s) id(s) by natural order</p>
     */
    private void setRootNodeIds(SortedSet<String> rootNodeIds) {
        this.rootNodeIds = rootNodeIds;
    }

    /**
     * <p>Summary:</p><p>Create database connection pool using Hikari</p>
     *
     * @return <p>A database connection pool instance</p>
     */
    private static Connection createConnection() {
        Connection connection = null;
        URL url = Thread.currentThread().getContextClassLoader().getResource("");
        assert url != null;
        HikariConfig config = new HikariConfig(url.getPath() + Scheme.HIKARI_PROPERTIES);
        HikariDataSource dataSource = new HikariDataSource(config);
        try {
            connection = dataSource.getConnection();
            logger.info(String.format("Database connect success! %s", connection.toString()));
        } catch (SQLException e) {
            logger.error(String.format("Database connect failed! %s", "Ops"));
            logger.error(e.getMessage());
        }

        return connection;
    }

    public static void main(String[] args) throws InterruptedException {
        long start = System.currentTimeMillis();
        Configuration xmlConfig = new XmlConfiguration(Resolver.class.getResource(Scheme.SLASH_EHCACHE_XML));

        try (CacheManager cacheManager = CacheManagerBuilder.newCacheManager(xmlConfig)) {
            cacheManager.init();
            Cache<String, ArrayList> cache = cacheManager.getCache("defaultCache", String.class, ArrayList.class);
            Resolver resolver = new Resolver();
            try (Connection connection = createConnection()) {
                String schemaName = connection.getSchema();
                if (StringUtils.isEmpty(schemaName)) {
                    schemaName = connection.getCatalog();
                }
                resolver.processEntityType(connection);

                List<Tables> lstTable = resolver.entities(connection, TABLES_TO_JSON_FILE_NAME);
                cache.put(Scheme.TABLES, (ArrayList) lstTable);
                resolver.collect(lstTable);
                List<Keys> lstFKRef = resolver.relationship(connection, FOREIGN_TO_JSON_FILE_NAME);
                cache.put(Scheme.FOREIGN_KEYS, (ArrayList) lstFKRef);

                Graph overview = resolver.overview(lstFKRef);
                resolver.setRootNodeIds(Toolkit.resolveDisconnectedGraph(overview));
                resolver.result(overview, schemaName);
            } catch (SQLException e) {
                logger.error(e.getMessage());
            }
            long end = System.currentTimeMillis();
            long duration = end - start;
            System.out.println(String
                .format(Scheme.TIME_DURATION_PROCESS, TimeUnit.MILLISECONDS.toMinutes(duration),
                    TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES
                        .toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration))));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * <p>Summary:</p><p>Visualize database schema corresponding tables relationship in it</p>
     *
     * @param overview   <p>Include all table(s) node in multiple unconnected component(s) in a single graph.</p>
     * @param schemaName <p>Database schema name</p>
     * @throws InterruptedException <p>Refer to concurrent executor service</p>
     */
    private void result(Graph overview, String schemaName) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        Future<Integer> status = executor.submit(genSQLScript(overview, schemaName));
        Future<List<Graph>> graphs = executor.submit(graphs(overview));
        try {
            System.out
                .println(status.get() == 0 ? "Success to generate SQL script!" : "Failed to generate SQL script!");
            List<Graph> lstGraph = graphs.get();
            viewClosedCounter = lstGraph.size();
            lstGraph.forEach(
                graph -> {
                    executor.execute(persists(graph, schemaName, GRAPH_FILE_NAME_PREFIX));

                    executor.execute(display(graph));
                }
            );
        } catch (ExecutionException e) {
            logger.error(e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    /**
     * <p>Summary:</p><p>Retrieve and process foreign key(s) reference among table(s),</p>
     * <p>merge foreign keys if multiple ones referred by a foreign table</p>
     *
     * @param connection <p>Database connection pool</p>
     * @param fileName   <p>Cached foreign key(s) JSON file, else the file does not exist</p>
     * @return <p>All processed foreign key(s) information</p>
     */
    private List<Keys> relationship(Connection connection, String fileName) {
        List<Keys> lstFKRef = null;
        Genson genson = new Genson();
        try {
            String schemaName = connection.getSchema();
            if (StringUtils.isEmpty(schemaName)) {
                schemaName = connection.getCatalog();
            }
            String destFileName = schemaName.toLowerCase() + "_" + fileName;
            File target = new File(destDir(schemaName) + destFileName);
            if (target.exists() && target.isFile() && target.canRead()) {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(target), Charset.forName(Scheme.UTF_8).newDecoder()))) {
                    lstFKRef = genson.deserialize(reader, new GenericType<List<Keys>>() {
                    });
                } catch (IOException e) {
                    logger.error(e.getMessage());
                }
            }
            if (lstFKRef == null) {
                lstFKRef = processFKRef(connection);
                persists(genson.serialize(lstFKRef, new GenericType<List<Keys>>() {
                }), FOREIGN_TO_JSON_FILE_NAME, schemaName);
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
        }
        return lstFKRef;
    }

    /**
     * <p>Summary:</p><p>Retrieve all table(s) in given schema of a specified database.</p>
     *
     * @param connection <p>Database connection pool</p>
     * @param fileName   <p>Cached table(s) JSON file, else the file does not exist</p>
     * @return <p>All retrieve table(s)</p>
     */
    private List<Tables> entities(Connection connection, String fileName) {
        List<Tables> lstTable = null;
        Genson genson = new Genson();
        try {
            String schemaName = connection.getSchema();
            if (StringUtils.isEmpty(schemaName)) {
                schemaName = connection.getCatalog();
            }
            String destFileName = schemaName.toLowerCase() + "_" + fileName;
            String destDir = destDir(schemaName);
            File target = new File(destDir + destFileName);
            if (target.exists() && target.isFile() && target.canRead()) {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(target), Charset.forName(Scheme.UTF_8).newDecoder()))) {
                    lstTable = genson.deserialize(reader, new GenericType<List<Tables>>() {
                    });
                } catch (IOException e) {
                    logger.error(e.getMessage());
                }
            }

            if (lstTable == null) {
                lstTable = processTable(connection);
                persists(genson.serialize(lstTable, new GenericType<List<Tables>>() {
                }), TABLES_TO_JSON_FILE_NAME, schemaName);
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
        }
        return lstTable;
    }

    /**
     * <p>Summary:</p><p>Store the data to file as JSON format, to avoid load database schema corresponding information each time.</p>
     *
     * @param contents <p>Data stream</p>
     * @param fileName <p>File name to write and keep in local file system</p>
     * @param prefix   <p>Prefix of file name</p>
     */
    private void persists(String contents, String fileName, String prefix) {
        initDir(prefix);
        String schema = prefix.toLowerCase() + "_";
        String destDir = destDir(prefix);
        File script =
            new File(destDir + schema + fileName);
        if (!script.exists() || !script.isFile()) {
            try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(destDir + schema + fileName, false),
                Charset.forName(Scheme.UTF_8).newEncoder())) {
                writer.write(contents);
                writer.flush();
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
    }

    @SuppressWarnings(value = {"unused"})
    private void retrieve(Connection connection, List<Tables> lstTables) {
        lstTables.forEach(table -> {
            try (ResultSet exportedKeys = connection.getMetaData()
                .getExportedKeys(connection.getCatalog(), connection.getSchema(), table.getTableName())) {
                processForeignKeys(exportedKeys, true);
            } catch (SQLException e) {
                logger.error(e.getMessage());
            }

            try (ResultSet importedKeys = connection.getMetaData()
                .getImportedKeys(connection.getCatalog(), connection.getSchema(), table.getTableName())) {
                processForeignKeys(importedKeys, false);
            } catch (SQLException e) {
                logger.error(e.getMessage());
            }

            try (ResultSet primaryKeys = connection.getMetaData()
                .getPrimaryKeys(connection.getCatalog(), connection.getSchema(), table.getTableName())) {
                processPrimaryKeys(primaryKeys);
            } catch (SQLException e) {
                logger.error(e.getMessage());
            }
        });
    }

    private void processPrimaryKeys(ResultSet primaryKeys) throws SQLException {
        while (primaryKeys.next()) {
            String catalog = primaryKeys.getString(Scheme.TABLE_CAT);
            String schema = primaryKeys.getString(Scheme.TABLE_SCHEM);
            String name = primaryKeys.getString(Scheme._TABLE_NAME);
            String column = primaryKeys.getString(Scheme.COLUMN_NAME);
            int sequence = primaryKeys.getShort(Scheme.KEY_SEQ);
            System.out.println(String
                .format("Catalog:%s & schema:%s & table:%s & column:%s & key sequence:%d", catalog, schema,
                    name, column, sequence));
        }
    }

    private void processForeignKeys(ResultSet keyResultSet, Boolean isExported) throws SQLException {
        while (keyResultSet.next()) {
            String pkTableName = keyResultSet.getString(Scheme.PKTABLE_NAME);
            String fkTableName = keyResultSet.getString(Scheme.FKTABLE_NAME);
            String fkColumnName = keyResultSet.getString(Scheme.FKCOLUMN_NAME);
            String pkColumn = keyResultSet.getString(Scheme.PKCOLUMN_NAME);
            int sequence = keyResultSet.getShort(Scheme.KEY_SEQ);
            System.out.println(String.format(
                "[Retrieve %s keys information] Primary table:%s & primary column:%s; foreign table:%s & foreign column:%s; key sequence:%d ",
                isExported ? "export" : "import",
                pkTableName, pkColumn, fkTableName, fkColumnName, sequence));
        }
    }


    /**
     * <p>Summary:</p><p>Display each graph, which is a single connected component.</p>
     *
     * @param graph <p>A graph, which will be displayed.</p>
     * @return <p>A runnable implement lambda</p>
     */
    private Runnable display(final Graph graph) {
        return () -> {
            Viewer viewer = graph.display();
            ViewerPipe pipe = viewer.newViewerPipe();
            pipe.addViewerListener(listener(graph));
            //Refer to https://github.com/graphstream/gs-core/issues/209
            pipe.addAttributeSink(graph);
            boolean loop = true;
            while (loop) {
                pipe.pump();
                loop = !graph.hasAttribute(Scheme.UI_VIEW_CLOSED);
                if (viewClosedCounter == 0) {
                    viewer.setCloseFramePolicy(Viewer.CloseFramePolicy.CLOSE_VIEWER);
                    viewer.close();
                }
            }
        };
    }

    /**
     * <p>Summary:</p><p>Write each graph as .gml format to local file system</p>
     *
     * @param graph      <p>A single graph, which only include a connected component.</p>
     * @param schemaName <p>Database schema or catalog.</p>
     * @param prefix     <p>Prefix of a graph file name.</p>
     * @return <p>A runnable implement by a lambda.</p>
     */
    private Runnable persists(Graph graph, String schemaName, String prefix) {
        return () -> {
            initDir(schemaName);
            String fileName = graphFileName(graph.getId(), schemaName, prefix);
            String destDir = destDir(schemaName);
            File target = new File(destDir + fileName);
            if (!target.exists() || !target.isFile()) {
                try {
                    graph.write(destDir + fileName);
                } catch (IOException e) {
                    logger.error(e.getMessage());
                }
            }
        };
    }

    /**
     * <p>Summary:</p><p>The destination directory to keep files.</p>
     *
     * @param schemaName <p>Database schema or catalog as subdirectory.</p>
     * @return <p>Destination directory.</p>
     */
    private String destDir(String schemaName) {
        return Scheme.WORK_DIR + schemaName + System.getProperty(Scheme.FILE_SEPARATOR);
    }

    /**
     * <p>Summary:</p><p>Initialize destination directory, ignore it if the directory exists, otherwise make all directories.</p>
     *
     * @param schemaName <p>Database schema or catalog, using as subdirectory.</p>
     */
    private void initDir(String schemaName) {
        File directory = new File(destDir(schemaName));
        if (!directory.exists() || !directory.isDirectory()) {
            if (!directory.mkdirs()) {
                logger.error("Create data collection directory failed!");
            }
        }
    }

    /**
     * <p>Summary:</p><p>Construct a name of graph file.</p>
     *
     * @param id         <p>The identifier of a graph.</p>
     * @param schemaName <p>Database schema or catalog, as a part of name of graph file.</p>
     * @param prefix     <p>Prefix of a graph file name.</p>
     * @return <p>A graph identifier's corresponding file name.</p>
     */
    private String graphFileName(String id, String schemaName, String prefix) {
        return schemaName.toLowerCase() + "_" + prefix + id.toLowerCase() + GRAPH_FILE_NAME_SUFFIX;
    }

    /**
     * <p>Summary:</p><p>Collection all table(s) name.</p>
     *
     * @param lstTable <p>Table list</p>
     */
    private void collect(List<Tables> lstTable) {
        lstTable.forEach(table -> tableIds.add(table.getTableName()));
    }

    /**
     * This method must be called after method <code>collect</code>
     */
    private StringBuilder genAuditLogScript() {
        StringBuilder complement = new StringBuilder(Scheme.STD_SQL_COMMENTS_BOILERPLATE_FOR_AUDIT_LOG_TABLES);
        tableIds.forEach(item -> {
            boolean exclusive = Toolkit.FILTER.matcher(item).matches();
            complement.append(String
                .format(
                    (exclusive ? "--" : "") + Scheme.STD_SQL_DELETE_STATEMENT,
                    StringUtils.lowerCase(item) + Scheme.AUDIT_LOG_TABLE_POSTFIX));
        });
        return complement;
    }

    /**
     * <p>Summary:</p><p>Assemble a multiple connected components in a single graph by all foreign references.</p>
     *
     * @param lstFKRef <p>Foreign key(s) reference list</p>
     * @return <p>A single graph with all foreign reference key(s).</p>
     */
    private Graph overview(List<Keys> lstFKRef) {
        //Remember processed tables position which corresponding to position in the list of nodes
        Graph overview = new GraphFactory().newInstance(Scheme.OVER_VIEW, SingleGraph.class.getName());
        if (!CollectionUtils.isEmpty(lstFKRef)) {
            lstFKRef.forEach(item -> build(overview, item));
        }
        return overview;
    }


    /**
     * <p>Summary:</p><p>Generate SQL script by traversal a graph with post-order.</p>
     *
     * @param graph      <p>A single graph with all foreign references relationship.</p>
     * @param schemaName <p>Database schema or catalog.</p>
     * @return <p>A indicator: <code>0</code>,success to generate SQL script file; otherwise failed.</p>
     */
    private Callable<Integer> genSQLScript(final Graph graph, String schemaName) {
        return () -> {
            String schema = schemaName.toLowerCase() + "_";
            File sql = new File(destDir(schemaName) + schema + SCRIPT_FILE_NAME);
            if (!sql.exists() || !sql.isFile()) {
                Set<String> untouched = new TreeSet<>(tableIds);
                rootNodeIds.forEach(
                    rootId -> Arrays.stream(rootId.split("[|]")).forEach(
                        rootNodeId -> {
                            Node root = graph.getNode(rootNodeId);
                            script.append(String.format(
                                Scheme.STD_SQL_COMMENTS_BOILERPLATE,
                                StringUtils.removeFirst(rootNodeId.toLowerCase(), Scheme.NODE_PREFIX)));
                            Toolkit.postTraverse(graph, root, untouched, script);
                        }
                    )
                );

                script.append(Scheme.STD_SQL_COMMENTS_BOILERPLATE_FOR_INDEPENDENT_TABLES);
                untouched.forEach(item -> {
                    boolean exclusive = Toolkit.FILTER.matcher(item).matches();
                    script.append(
                        String.format(
                            (exclusive ? "--" : "") + Scheme.STD_SQL_DELETE_STATEMENT,
                            StringUtils.lowerCase(item)));
                });

                script.append(genAuditLogScript());

                script.append(Scheme.STD_SQL_COMMIT);
                persists(script.toString(), SCRIPT_FILE_NAME, schemaName);
            }
            return 0;
        };
    }

    /**
     * <p>Summary:</p><p>Build a single graph with all foreign reference keys.</p>
     *
     * @param graph <p>A single graph, possibly include more than one connected components.</p>
     * @param item  <p>Each foreign reference key information.</p>
     */
    private void build(Graph graph, IRelevance<String, List<String>> item) {
        String targetNodeId = Scheme.NODE_PREFIX + item.to();
        String sourceNodeId = Scheme.NODE_PREFIX + item.from();
        String linkEdgeId = Scheme.EDGE_PREFIX + item.to() + item.from();
        Node target, source;
        int status = present(graph, item);
        switch (status) {
            case Scheme.BOTH_NODES_ABSENT:
                target = graph.addNode(targetNodeId);
                source = graph.addNode(sourceNodeId);
                break;
            case Scheme.ONLY_TARGET_NODE_PRESENTS:
                target = graph.getNode(targetNodeId);
                source = graph.addNode(sourceNodeId);
                break;
            case Scheme.ONLY_SOURCE_NODE_PRESENTS:
                target = graph.addNode(targetNodeId);
                source = graph.getNode(sourceNodeId);
                break;
            default:
                target = graph.getNode(targetNodeId);
                source = graph.getNode(sourceNodeId);
        }
        Toolkit.addNodeInfo(item, target, source);
        Edge edge = graph.addEdge(linkEdgeId, sourceNodeId, targetNodeId, true);
        Toolkit.addEdgeInfo(item, edge);
    }

    /**
     * <p>Summary:</p><p>If there are amount of less than 2 depth graph(s), filter to display which can avoid CPU overhead usage and promote response performance.</p>
     *
     * @param overview <p>A single graph with all foreign reference key(s).</p>
     * @return <p>The filtered graph(s) to display.</p>
     */
    private Callable<List<Graph>> graphs(final Graph overview) {
        final List<Graph> graphs = new ArrayList<>();
        return () -> {
            rootNodeIds.forEach(rootId -> Arrays.stream(rootId.split("[|]")).filter(nodeId ->
                Toolkit.height(overview.getNode(nodeId)) >= HEIGHT_THRESHOLD).findFirst()
                .ifPresent(nodeId -> graphs.add(graph(overview.getNode(nodeId)))));
            return graphs;
        };
    }

    /**
     * <p>Summary:</p><p>Assemble each graph which depth is filtered and need to display</p>
     *
     * @param root <p>A root node</p>
     * @return <p>A single graph which only contains a connected component.</p>
     */
    private Graph graph(Node root) {
        Graph result =
            new GraphFactory()
                .newInstance(StringUtils.remove(root.getId(), Scheme.NODE_PREFIX), SingleGraph.class.getName());
        result.addAttribute(Scheme.UI_QUALITY);
        result.addAttribute(Scheme.UI_ANTIALIAS);
        root.getBreadthFirstIterator(false)
            .forEachRemaining(currentNode -> currentNode.getEnteringEdgeSet().forEach(
                edge -> {
                    assembleGraph(result, edge);
                }
            ));
        Toolkit.nodeSize(result, 1, 5);
        result.addAttribute(Scheme.UI_DEFAULT_TITLE, StringUtils.remove(root.getId(), Scheme.NODE_PREFIX));
        result.addAttribute(Scheme.UI_STYLESHEET, "url(css/polish.css)");
        return result;
    }

    /**
     * <p>Summary:</p><p>Assemble a single graph with only one connected component.</p>
     *
     * @param result <p>A single graph is about to  be assembled</p>
     * @param edge   <p>A existed single graph's edge.</p>
     */
    private void assembleGraph(Graph result, Edge edge) {
        if (result.getEdge(edge.getId()) == null) {
            Node source = edge.getSourceNode();
            Node target = edge.getTargetNode();
            if (result.getNode(target.getId()) == null) {
//            result.nodeFactory().newInstance(target.getId(),result);
                Node parent = result.addNode(target.getId());
                target.getAttributeKeySet()
                    .forEach(key -> parent.addAttribute(key, target.<String>getAttribute(key)));
            }
            if (result.getNode(source.getId()) == null) {
                Node child = result.addNode(source.getId());
                source.getAttributeKeySet()
                    .forEach(key -> child.addAttribute(key, source.<String>getAttribute(key)));
            }
            result.addEdge(edge.getId(), source.getId(), target.getId(), true);
            edge.getAttributeKeySet()
                .forEach(key -> result.getEdge(edge.getId())
                    .addAttribute(key, edge.<String>getAttribute(key)));
        }
    }

    /**
     * <p>Summary:</p><p>Determine whether current edge related two nodes are existing or not.</p>
     *
     * @param graph <p>A single graph.</p>
     * @param item  <p>Foreign reference information, include primary table, foreign table, foreign keys and etc.</p>
     * @return <p>A present status.</p>
     */
    private int present(Graph graph, IRelevance<String, List<String>> item) {
        String targetId = Scheme.NODE_PREFIX + item.to();
        String sourceId = Scheme.NODE_PREFIX + item.from();
        int target = graph.getNode(targetId) != null ? Scheme.ONLY_TARGET_NODE_PRESENTS : 0;
        int source = graph.getNode(sourceId) != null ? Scheme.ONLY_SOURCE_NODE_PRESENTS : 0;
        return target | source;
    }

    /**
     * <p>Summary:</p><p>Retrieve all schema entities' type, which are {<code>Table</code>,<code>View</code>,etc.}</p>
     *
     * @param connection <p>A database connection pool instance.</p>
     * @return <p>Entities type list.</p>
     * @throws SQLException <p>SQL exception while retrieve those information.</p>
     */
    private List<String> processEntityType(Connection connection) throws SQLException {
        List<String> lstEntityTypes = new ArrayList<>();
        ResultSet types = connection.getMetaData().getTableTypes();
        while (types.next()) {
            lstEntityTypes.add(types.getString(Scheme._TABLE_TYPE));
        }
        lstEntityTypes.forEach(System.out::println);
        return lstEntityTypes;
    }

    /**
     * <p>Summary:</p><p>Retrieve tables and map it to an object</p>
     *
     * @param connection <p>A database connection pool instance.</p>
     * @return <p>Table list</p>
     */
    private List<Tables> processTable(Connection connection) {
        List<Tables> lstTable = new ArrayList<>();
        try (ResultSet tables =
                 connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), null,
                     new String[]{Scheme.ENTITY_TABLE})) {
            ResultSetMetaData meta = tables.getMetaData();
            int columns = meta.getColumnCount();
            Set<String> nameSet = new HashSet<>(columns);
            for (int i = 1; i < columns; i++) {
                nameSet.add(meta.getColumnName(i));
            }

            while (tables.next()) {
                Tables tableMeta = new Tables();
                pushTableSummaryInfo(tables, lstTable, nameSet, tableMeta);
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
        }
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~#Tables information list#~~~~~~~~~~~~~~~~~~~~~~~");
        lstTable.forEach(System.out::println);
        return lstTable;
    }

    @SuppressWarnings(value = {"unused"})
    private List<Keys> processPKRef(final Connection connection) {
        List<Keys> lstPrimaryKey = pushPrimaryKeyInfo(connection);
        System.out.println(
            "~~~~~~~~~~~~~~~~~~~~~~~#Tables primary keys reference information list#~~~~~~~~~~~~~~~~~~~~~~~");
        lstPrimaryKey.forEach(System.out::println);
        return lstPrimaryKey;
    }

    private List<Keys> processFKRef(final Connection connection) {
        List<Keys> lstForeignKey = pushForeignKeyInfo(connection);
        System.out.println(
            "~~~~~~~~~~~~~~~~~~~~~~~#Tables foreign keys reference information list#~~~~~~~~~~~~~~~~~~~~~~~");
        lstForeignKey.forEach(System.out::println);
        return lstForeignKey;
    }

    private List<Keys> pushForeignKeyInfo(Connection connection) {
        return processKeysInfo(connection, true);
    }

    /**
     * <p>Summary:</p><p>Handle each foreign reference key information.</p>
     *
     * @param result     <p>Foreign reference key information result set.</p>
     * @param lstRefKeys <p>Foreign reference key list</p>
     * @param mapIds     <p>Processed foreign key(s) set.</p>
     * @throws SQLException <p>SQL exception while process foreign reference key result set.</p>
     */
    private void handleRefKeys(ResultSet result, List<Keys> lstRefKeys, Map<String, Integer> mapIds)
        throws SQLException {
        while (result.next()) {
            String pkTableName = result.getString(Scheme.PKTABLE_NAME);
            String fkTableName = result.getString(Scheme.FKTABLE_NAME);
            String fkColumnName = result.getString(Scheme.FKCOLUMN_NAME);
            String symbol = String.format("<%s>$<%s>", pkTableName, fkTableName);
            Integer pos = MapUtils.getInteger(mapIds, symbol);
            if (pos == null) {
                Keys foreignKeyInfo = new Keys();
                foreignKeyInfo.setPkTableName(pkTableName);
                foreignKeyInfo.addPkColumnName(result.getString(Scheme.PKCOLUMN_NAME));
                foreignKeyInfo.setFkTableName(fkTableName);
                foreignKeyInfo.addFkColumnName(fkColumnName);
                foreignKeyInfo.setKeySequence(result.getShort(Scheme.KEY_SEQ));
                lstRefKeys.add(foreignKeyInfo);
                mapIds.put(symbol, lstRefKeys.size() - 1);
            } else {
                IRelevance<String, List<String>> reference = lstRefKeys.get(pos);
                reference.set(fkColumnName);
            }
        }
    }

    private List<Keys> pushPrimaryKeyInfo(Connection connection) {
        return processKeysInfo(connection, false);
    }

    /**
     * <p>Summary:</p><p>Pagination query to retrieve foreign reference keys information.</p>
     *
     * @param connection    <p>A database connection pool instance.</p>
     * @param forExportKeys <p><code>true</code>, export keys request; otherwise <code>false</code>.</p>
     * @return <p>All foreign reference key(s) list.</p>
     */
    private List<Keys> processKeysInfo(Connection connection, boolean forExportKeys) {
        List<Keys> lstRefKeys = new ArrayList<>();
        try {
            IDAOFactory factory = new DAOFactory();
            String productName = connection.getMetaData().getDatabaseProductName();
            IDAOForMeta daoMeta = factory.createDBScheme(productName);
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                forExportKeys ? daoMeta.exportedKeys() : daoMeta.importedKeys())) {
                int times = 0;
                boolean hasNext = true;
                Map<String, Integer> identifiers = new WeakHashMap<>();
                do {
                    switch (productName) {
                        case Scheme.DB_TYPE_ORACLE:
                            preparedStatement.setString(1, connection.getSchema());
                            preparedStatement.setString(2, connection.getSchema());
                            preparedStatement.setInt(3, times * FIXED_ROW_COUNT + 1);
                            preparedStatement.setInt(4, (times + 1) * FIXED_ROW_COUNT);
                            break;
                        case Scheme.DB_TYPE_MYSQL:
                            preparedStatement.setString(1, connection.getCatalog());
                            preparedStatement.setInt(2, times * FIXED_ROW_COUNT + 1);
                            preparedStatement.setInt(3, FIXED_ROW_COUNT);
                            break;
                        default:
                            throw new UnIdentifiedException(productName);
                    }
                    try (ResultSet refKeyResult = preparedStatement.executeQuery()) {
                        refKeyResult.setFetchDirection(ResultSet.TYPE_FORWARD_ONLY);
                        if (hasNext = refKeyResult.isBeforeFirst()) {
                            handleRefKeys(refKeyResult, lstRefKeys, identifiers);
                        }
                    } catch (SQLException e) {
                        logger.error(e.getMessage());
                    }
                    ++times;
                } while (hasNext);
            } catch (SQLException | UnIdentifiedException e) {
                logger.error(e.getMessage());
            }
        } catch (SQLException | UnIdentifiedException e) {
            logger.error(e.getMessage());
        }
        return lstRefKeys;
    }

    private static void pushTableSummaryInfo(
        ResultSet tables, List<Tables> lstTable, Set<String> nameSet, Tables tableMeta) throws SQLException {

        if (nameSet.contains(Scheme.TABLE_CAT)) {
            tableMeta.setTableCatalog(tables.getString(Scheme.TABLE_CAT));
        }
        if (nameSet.contains(Scheme.TABLE_SCHEM)) {
            tableMeta.setTableSchema(tables.getString(Scheme.TABLE_SCHEM));
        }
        if (nameSet.contains(Scheme._TABLE_NAME)) {
            tableMeta.setTableName(tables.getString(Scheme._TABLE_NAME));
        }
        if (nameSet.contains(Scheme._TABLE_TYPE)) {
            tableMeta.setTableType(tables.getString(Scheme._TABLE_TYPE));
        }
        if (nameSet.contains(Scheme.REMARKS)) {
            tableMeta.setTableRemark(tables.getString(Scheme.REMARKS));
        }
        if (nameSet.contains(Scheme.TYPE_CAT)) {
            tableMeta.setTypeCatalog(tables.getString(Scheme.TYPE_CAT));
        }
        if (nameSet.contains(Scheme.TYPE_SCHEM)) {
            tableMeta.setTypeSchema(tables.getString(Scheme.TYPE_SCHEM));
        }
        if (nameSet.contains(Scheme._TYPE_NAME)) {
            tableMeta.setTypeName(tables.getString(Scheme._TYPE_NAME));
        }
        if (nameSet.contains(Scheme.SELF_REFERENCING_COL_NAME)) {
            tableMeta.setTableSelfRefColName(tables.getString(Scheme.SELF_REFERENCING_COL_NAME));
        }
        if (nameSet.contains(Scheme.REF_GENERATION)) {
            tableMeta.setTableRefGeneration(tables.getString(Scheme.REF_GENERATION));
        }
        lstTable.add(tableMeta);
    }

    private ViewerListener listener(Graph graph) {
        return new ViewerListener() {
            @Override
            public void viewClosed(String viewName) {
                --viewClosedCounter;
                System.out.println(String.format("Graphs[%s] ara closed!", graph.getId()));
            }

            @Override
            public void buttonPushed(String id) {
                Node node = graph.getNode(id);
                if (node != null) {
                    for (Edge edge : node.getEachLeavingEdge()) {
                        edge.setAttribute(Scheme.TEXT_MODE, Scheme.SHOW_TIPS);
                    }
                }
            }

            @Override
            public void buttonReleased(String id) {
                Node node = graph.getNode(id);
                if (node != null) {
                    for (Edge edge : node.getEachLeavingEdge()) {
                        edge.setAttribute(Scheme.TEXT_MODE, Scheme.HIDE_TIPS);
                    }
                }
            }
        };
    }
}
