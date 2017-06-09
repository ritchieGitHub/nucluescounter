package at.ac.ist.fiji.cellcounter;
/*
 * ODODD.java Created on 23 November 2004, 22:56
 */

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ListIterator;
import java.util.Vector;

/**
 * @author kurt
 */
public class WriteXML {

    private OutputStream XMLFileOut;

    private OutputStream XMLBuffOut;

    private OutputStreamWriter out;

    /**
     * Creates a new instance of ODWriteXMLODD
     */
    public WriteXML(String XMLFilepath) {
        try {
            this.XMLFileOut = new FileOutputStream(XMLFilepath); // add FilePath
            this.XMLBuffOut = new BufferedOutputStream(this.XMLFileOut);
            this.out = new OutputStreamWriter(this.XMLBuffOut, "UTF-8");
        } catch (FileNotFoundException e) {
            System.out.println("File Not Found " + e.getMessage());
        } catch (UnsupportedEncodingException e) {
            System.out.println("This VM does not support the UTF-8 character set. " + e.getMessage());
        }
    }

    public boolean writeXML(String imgFilename, Vector typeVector, int currentType) {
        try {
            this.out.write("<?xml version=\"1.0\" ");
            this.out.write("encoding=\"UTF-8\"?>\r\n");
            this.out.write("<CellCounter_Marker_File>\r\n");

            // write the image properties
            this.out.write(" <Image_Properties>\r\n");
            this.out.write("     <Image_Filename>" + imgFilename + "</Image_Filename>\r\n");
            this.out.write(" </Image_Properties>\r\n");

            // write the marker data
            this.out.write(" <Marker_Data>\r\n");
            this.out.write("     <Current_Type>" + currentType + "</Current_Type>\r\n");
            ListIterator it = typeVector.listIterator();
            while (it.hasNext()) {
                CellCntrMarkerVector markerVector = (CellCntrMarkerVector) it.next();
                int type = markerVector.getType();
                this.out.write("     <Marker_Type>\r\n");
                this.out.write("         <Type>" + type + "</Type>\r\n");
                ListIterator lit = markerVector.listIterator();
                while (lit.hasNext()) {
                    CellCntrMarker marker = (CellCntrMarker) lit.next();
                    int x = marker.getX();
                    int y = marker.getY();
                    int z = marker.getZ();
                    this.out.write("         <Marker>\r\n");
                    this.out.write("             <MarkerX>" + x + "</MarkerX>\r\n");
                    this.out.write("             <MarkerY>" + y + "</MarkerY>\r\n");
                    this.out.write("             <MarkerZ>" + z + "</MarkerZ>\r\n");
                    this.out.write("         </Marker>\r\n");
                }
                this.out.write("     </Marker_Type>\r\n");
            }

            this.out.write(" </Marker_Data>\r\n");
            this.out.write("</CellCounter_Marker_File>\r\n");

            this.out.flush(); // Don't forget to flush!
            this.out.close();
            return true;
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

}
