package br.com.smart.jmeter.plugins;

import java.io.File;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class Conversor {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception{

		
		String arquivo = "C:\\Users\\Domingos\\Projetos\\smart\\cpqd\\performance\\pan5.xml";
		if(args.length > 0){
			arquivo = args[0];
		}
		SAXParserFactory newInstance = SAXParserFactory.newInstance();
		SAXParser newSAXParser = newInstance.newSAXParser();
		newSAXParser.parse(new File(arquivo), new Conv());

	}

	
	static class Conv extends DefaultHandler{

		
		long counter = 0;
		
		
		StringBuffer url;
		StringBuffer sb;
		boolean isValue;
		boolean isFirst = true;
		
		boolean skipFirst = true;
		
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) throws SAXException {
			if("HTTPSamplerProxy".equals(qName)){
				url = new StringBuffer();
				criacabecalho();
				url.append(attributes.getValue("testname"));
				isValue=false;
				sb = null;
				isFirst = true;
				skipFirst = true;
			} else if(url != null && "stringProp".equals(qName) && "Argument.value".equals(attributes.getValue("name"))){
				isValue=true;
				sb = new StringBuffer();
			} else if(url != null &&  "elementProp".equals(qName)){
				if(skipFirst){
					skipFirst = false;
					return;
				}
				if(isFirst){
					url.append("?");
				} else {
					url.append("&amp;");
				}
				url.append(attributes.getValue("name").replace(" ", "%20"));
				url.append("=");
				isFirst = false;
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			
			//terminou a url
			if("HTTPSamplerProxy".equals(qName)){
				fechaentrada();
				System.out.print(url.toString());
				url = null;
			} else if(url != null && "stringProp".equals(qName) && isValue){
				isValue=false;
				url.append(sb.toString().replace(" ", "%20"));
				sb = null;
			}
		}

		@Override
		public void characters(char[] ch, int start, int length)
				throws SAXException {
			if(sb != null){
				sb.append(ch,start,length);
			}
		}
		
		
		
		void criacabecalho(){
			
			url.append("              <elementProp name=\"path_");
			url.append(++counter);
			url.append("\" elementType=\"Argument\">\n");
			url.append("                <stringProp name=\"Argument.name\">path_");
			url.append(counter);
			url.append("</stringProp>\n");
			url.append("                <stringProp name=\"Argument.value\">");
		}
		
		void fechaentrada(){
			url.append("</stringProp>\n");
			url.append("                <stringProp name=\"Argument.metadata\">=</stringProp>\n");
			url.append("              </elementProp>\n");
		}
	}
}
