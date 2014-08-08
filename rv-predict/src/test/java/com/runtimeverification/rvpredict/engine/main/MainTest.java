package com.runtimeverification.rvpredict.engine.main;

import com.runtimeverification.rvpredict.IntegrationTest;
import com.runtimeverification.rvpredict.TestHelper;
import config.Configuration;
import org.junit.experimental.categories.Category;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import rvpredict.engine.main.Main;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;


/**
 * Base class for rv-predict system tests
 * @author TraianSF
 */
@RunWith(Parameterized.class)
@Category(IntegrationTest.class)
public class MainTest {
    private static String basePath = System.getProperty("rvPath");
    private static String separator = System.getProperty("file.separator");

    private static String getTestConfigPath() {
        String path = null;
        try {
            path = Paths.get(MainTest.class.getResource("/test.xml").toURI()).toAbsolutePath().toString();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return path;
    }

    private static String rvPredictJar = basePath + separator + "lib" + separator + "rv-predict-engine.jar";
    private static String java = org.apache.tools.ant.util.JavaEnvUtils.getJreExecutable("java");
    private static List<String> rvArgList = Arrays.asList(new String[]{java, "-cp", rvPredictJar,
            "rvpredict.engine.main.Main"});
    String[] command;
    TestHelper helper;
    String name;
    List<String> args;


    public MainTest(String name, String specPath, String... command) {
        this.name = name;
        helper = new TestHelper(specPath);
        this.command = command;
        args = new ArrayList<>(rvArgList);
        args.addAll(Arrays.asList(command));
    }

    /**
     * Builds the tests, then runs them.
     * Matches precomputed expected output files against the output generated by the tests.
     * @throws Exception
     */
    @Test
    public void testTest() throws Exception {
        System.out.println(name);
        String[] args = new String[this.args.size()];
        this.args.toArray(args);
        System.out.println(Arrays.toString(args));
        helper.testCommand(null, args);
    }

    // The method bellow creates the set of parameter instances to be used as seeds by
    // the test constructor.  Junit will run the testsuite once for each parameter instance.
    // This is documented in the Junit Parameterized tests page:
    // http://junit.sourceforge.net/javadoc/org/junit/runners/Parameterized.html
    @Parameterized.Parameters(name="{0}")
    public static Collection<Object[]> data() {
        Collection<Object[]> data = new ArrayList<Object[]>();
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(new File(getTestConfigPath()));
            NodeList tests = document.getElementsByTagName("test");
            for (int i = 0; i < tests.getLength(); i++) {
                Node node = tests.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element test = (Element) node;
                    String name = test.getAttribute("name");
                    List<String> arguments = new ArrayList<String>();
                    NodeList args = test.getElementsByTagName("arg");
                    for (int j = 0; j < args.getLength(); j++) {
                        node = args.item(j);
                        if (node.getNodeType() == Node.ELEMENT_NODE) {
                            Element arg = (Element) node;
                            String key = arg.getAttribute("key");
                            if (!key.isEmpty()) {
                                arguments.add(key);
                            }
                            arguments.add(arg.getAttribute("value"));
                        }
                    }
                    String[] sArgs = new String[arguments.size()];
                    data.add(new Object[]{ name, basePath, arguments.toArray(sArgs)});
                }
            }
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }
}
