/*******************************************************************************
 * Copyright 2019, 2020, 2022 Thomas Theussing and Contributors
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110, USA
 *
 *******************************************************************************/

package com.telemaxx.mapsforgesrv;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * reading Mapsforge theme. after making instance call readXML
 * @author telemaxx
 * @see http://www.vogella.com/tutorials/JavaXML/article.html
 *
 */
public class MapsforgeStyleParser {
	static final String ID              = "id";           //$NON-NLS-1$
	static final String XML_LAYER       = "layer";        //$NON-NLS-1$
	static final String STYLE_MENU      = "stylemenu";    //$NON-NLS-1$
	static final String VISIBLE         = "visible";      //$NON-NLS-1$
	static final String NAME            = "name";         //$NON-NLS-1$
	static final String LANG            = "lang";         //$NON-NLS-1$
	static final String DEFAULTLANG     = "defaultlang";  //$NON-NLS-1$
	static final String DEFAULTSTYLE    = "defaultvalue"; //$NON-NLS-1$
	static final String VALUE           = "value";        //$NON-NLS-1$
	static Boolean Style = false;
	String na_language = ""; //$NON-NLS-1$
	String na_value = ""; //$NON-NLS-1$
	String defaultlanguage = ""; //$NON-NLS-1$
	String defaultstyle = ""; //$NON-NLS-1$

	final static Logger logger = LoggerFactory.getLogger(MapsforgeStyleParser.class);

	/**
	 * just for test purposes
	 * @param args
	 */
	public static void main(final String args[]) {
		final MapsforgeStyleParser mapStyleParser = new MapsforgeStyleParser();
		final List<Style> styles = mapStyleParser.readXML("C:\\Users\\top\\BTSync\\oruxmaps\\mapstyles\\ELV4\\Elevate.xml"); //$NON-NLS-1$
		logger.info("Stylecount: " + styles.size()); //$NON-NLS-1$
		logger.info("Defaultlanguage: " + mapStyleParser.getDefaultLanguage()); //$NON-NLS-1$
		logger.info("Defaultstyle:    " + mapStyleParser.getDefaultStyle()); //$NON-NLS-1$
		//logger.info("Defaultstylename de:" + styles.);
		for (final Style style : styles) {
			logger.info(style.toString());
			logger.info("local Name: " + style.getName(Locale.getDefault().getLanguage())); //$NON-NLS-1$
			//logger.info("local Name: " + style.getName("de_DE")); //$NON-NLS-1$ //$NON-NLS-2$
			//logger.info("localisation " + Locale.getDefault().getCountry());
		}
	}

	public String getDefaultLanguage() {
		return defaultlanguage;
	}

	public String getDefaultStyle() {
		return defaultstyle;
	}

	/**
	 * reading Mapsforge theme and return a list with selectable layers
	 * @param xmlFile
	 * @return a list with available, visible layers
	 */
	@SuppressWarnings({ })
	public List<Style> readXML(final String xmlFile) {
		final List<Style> items = new ArrayList<>();
		try {
			final InputStream xmlFileStream = new FileInputStream(xmlFile);
			items.addAll(readXML(xmlFileStream));
		} catch (final Exception e) {
			logger.error(e.getMessage(),e);
		}
		return items;
	}
	/**
	 * reading Mapsforge theme and return a list with selectable layers
	 * @param xmlFileStream
	 * @return a list with available, visible layers
	 */
	public List<Style> readXML(final InputStream xmlFileStream) {
		final List<Style> items = new ArrayList<>();
		try {
			// First, create a new XMLInputFactory
			final XMLInputFactory inputFactory = XMLInputFactory.newInstance();
			// Setup a new eventReader
			final XMLEventReader eventReader = inputFactory.createXMLEventReader(xmlFileStream);
			// read the XML document
			Style item = null;

			while (eventReader.hasNext()) {
				XMLEvent event = eventReader.nextEvent();
				if (event.isStartElement()) {
					final StartElement startElement = event.asStartElement();
					// if stylemenue, getting the defaults
					if (startElement.getName().getLocalPart().equals(STYLE_MENU)) {
						final Iterator<Attribute> sm_attributes = startElement.getAttributes();
						while (sm_attributes.hasNext()) { //in the same line as <layer>
							final Attribute sm_attribute = sm_attributes.next();
							if (sm_attribute.getName().toString().equals(DEFAULTLANG)) {
								defaultlanguage = sm_attribute.getValue();
							} else if (sm_attribute.getName().toString().equals(DEFAULTSTYLE)) {
								defaultstyle =  sm_attribute.getValue();
								//logger.info("default style: " + defaultstyle);
							}
						}
					}
					// If we have an item(layer) element, we create a new item
					if (startElement.getName().getLocalPart().equals(XML_LAYER)) {
						Style = false;
						item = new Style();
						final Iterator<Attribute> attributes = startElement.getAttributes();
						while (attributes.hasNext()) { //in the same line as <layer>
							final Attribute attribute = attributes.next();
							if (attribute.getName().toString().equals(ID)) {
								item.setXmlLayer(attribute.getValue());
							}
							if (attribute.getName().toString().equals(VISIBLE)) {
								if(attribute.getValue().equals("true")){ //$NON-NLS-1$
									Style = true;
								}
							}
						}
					}
					if (event.isStartElement()) {
						if (event.asStartElement().getName().getLocalPart().equals(NAME)) {
							final Iterator<Attribute> name_attributes = startElement.getAttributes();
							while (name_attributes.hasNext()) { //in the same line as <layer>
								final Attribute name_attribute = name_attributes.next();
								if (name_attribute.getName().toString().equals(LANG)){
									na_language = name_attribute.getValue();
								} else if (name_attribute.getName().toString().equals(VALUE)){
									na_value = name_attribute.getValue();
								}
							}
							if (Style) {
								item.setName(na_language, na_value);
							}
							event = eventReader.nextEvent();
							continue;
						}
					}
				}
				// If we reach the end of an item element, we add it to the list
				if (event.isEndElement()) {
					final EndElement endElement = event.asEndElement();
					if (endElement.getName().getLocalPart().equals(XML_LAYER) && Style) {
						item.setDefaultLanguage(defaultlanguage);
						items.add(item);
					}
				}
			}
		} catch (final Exception e) {
			logger.error(e.getMessage(),e);
		}
		return items;
	} //end ReadConfig
}

/**
 * Bean Style containes a visible Style
 * @author telemaxx
 *
 */

class Style {
	private Map<String, String> name = new HashMap<>();
	private String xmlLayer;
	private String defaultlanguage = "de"; //$NON-NLS-1$

	public String getDefaultLaguage() {
		return defaultlanguage;
	}

	/**
	 * getting the name as map with all localizations
	 * @return Map<String language,String name>
	 */
	public Map<String, String> getName() {
		return name;
	}

	/**
	 * getting a local name of the mapstyle
	 * @param language string like "en"
	 * @return a String with the local name like "hiking"
	 */
	public String getName(final String language) {
		if (language.equals("default")){ //$NON-NLS-1$
			return name.get(defaultlanguage);
		} else
			if (name.containsKey(language)) {
				return name.get(language);
			} else {
				return name.get(defaultlanguage);
			}
	}
	/**
	 * get the style name like
	 * @return String containing the stylename like "elv-mtb"
	 */
	public String getXmlLayer() {
		return xmlLayer;
	}

	public void setDefaultLanguage(final String language) {
		this.defaultlanguage = language;
	}

	/**
	 * set the style name with a given language
	 * @param language
	 * @param name
	 */
	public void setName(final String language, final String name) {
		//logger.info("setname: " + language + " name: " + name);
		this.name.put(language, name);
	}

	public void setXmlLayer(final String xmlLayer) {
		this.xmlLayer = xmlLayer;
	}

	@Override
	public String toString() {
		return "Item [xmlLayer=" + xmlLayer + " Name= " + name.get(defaultlanguage) + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
