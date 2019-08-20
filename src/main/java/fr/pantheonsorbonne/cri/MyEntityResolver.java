package fr.pantheonsorbonne.cri;

import java.io.File;
import java.io.IOException;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class MyEntityResolver implements EntityResolver {

    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {

       // Grab only the filename part from the full path
      String filename = new File(systemId).getName();

      // Now prepend the correct path
      String correctedId = filename;

      InputSource is = new InputSource(ClassLoader.getSystemResourceAsStream(correctedId));
      is.setSystemId(correctedId);

      return is;
   }

}