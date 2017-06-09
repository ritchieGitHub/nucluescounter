package at.ac.ist.fiji.cellcounter;
/*
 * ODReadXMLODD.java Created on 27 November 2004, 10:47
 */

import java.io.File;
import java.io.IOException;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author kurt
 */
public class ReadXML {

    private boolean verbose;

    private DocumentBuilderFactory dbf;

    private DocumentBuilder db;

    private Document doc;

    private String str;

    public static final int IMAGE_FILE_PATH = 0;

    public static final int CURRENT_TYPE = 1;

    /**
     * Creates a new instance of ODReadXMLODD
     */
    public ReadXML(String XMLFilePath) {
        setVerbose(this.verbose);
        try {
            this.dbf = DocumentBuilderFactory.newInstance();
            this.db = this.dbf.newDocumentBuilder();
            this.doc = this.db.parse(new File(XMLFilePath));
            this.doc.getDocumentElement().normalize();
        } catch (SAXException e) {
            System.out.println(e.getMessage());
            System.out.println(XMLFilePath + " is not well-formed.");
        } catch (IOException e) {
            System.out.println("IOException " + e.getMessage());
        } catch (ParserConfigurationException e) {
            System.out.println("ParserConfigurationException " + e.getMessage());
        }
    }

    public String readImgProperties(int valueID) { // as URL
        switch (valueID) {
            case IMAGE_FILE_PATH:
                this.str = readSingleValue(this.doc, "Image_Filename");
                break;
            case CURRENT_TYPE:
                this.str = readSingleValue(this.doc, "Current_Type");
                break;
        }
        if (this.str != null) {
            return this.str;
        }
        return null;
    }

    public Vector readMarkerData() {
        Vector typeVector = new Vector();

        NodeList markerTypeNodeList = getNodeListFromTag(this.doc, "Marker_Type");
        for (int i = 0; i < markerTypeNodeList.getLength(); i++) {
            Element markerTypeElement = getElement(markerTypeNodeList, i);
            NodeList typeNodeList = markerTypeElement.getElementsByTagName("Type");
            CellCntrMarkerVector markerVector = new CellCntrMarkerVector(Integer.parseInt(readValue(typeNodeList, 0)));

            NodeList markerNodeList = markerTypeElement.getElementsByTagName("Marker");
            for (int j = 0; j < markerNodeList.getLength(); j++) {
                Element markerElement = getElement(markerNodeList, j);
                NodeList markerXNodeList = markerElement.getElementsByTagName("MarkerX");
                NodeList markerYNodeList = markerElement.getElementsByTagName("MarkerY");
                NodeList markerZNodeList = markerElement.getElementsByTagName("MarkerZ");
                CellCntrMarker marker = new CellCntrMarker();
                marker.setX(Integer.parseInt(readValue(markerXNodeList, 0)));
                marker.setY(Integer.parseInt(readValue(markerYNodeList, 0)));
                marker.setZ(Integer.parseInt(readValue(markerZNodeList, 0)));
                markerVector.add(marker);
            }
            typeVector.add(markerVector);
        }
        return typeVector;
    }

    private String readValue(NodeList nodeList, int index) throws NullPointerException {
        Element element = getElement(nodeList, index);
        debugReport("Element = " + element.getNodeName());
        NodeList elementNodeList = getChildNodes(element);
        String str = getValue(elementNodeList, 0);
        return str;
    }

    private String readSingleValue(Document doc, String elementName) {
        NodeList nodeList = getNodeListFromTag(doc, elementName);
        Element element = getElement(nodeList, 0);
        nodeList = getChildNodes(element);
        String str = getValue(nodeList, 0);
        return str;
    }

    private NodeList getNodeListFromTag(Document doc, String elementName) {
        NodeList nodeList = doc.getElementsByTagName(elementName);
        return nodeList;
    }

    private NodeList getChildNodes(Element element) {
        NodeList nodeList = element.getChildNodes();
        return nodeList;
    }

    private Element getElement(NodeList nodeList, int index) {
        Element element = (Element) nodeList.item(index);
        return element;
    }

    private String getValue(NodeList nodeList, int index) {
        String str = nodeList.item(index).getNodeValue().trim();
        return str;
    }

    public void debugReport(String report) {
        if (this.verbose) {
            System.out.println(report);
        }
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isVerbose() {
        return this.verbose;
    }
}
