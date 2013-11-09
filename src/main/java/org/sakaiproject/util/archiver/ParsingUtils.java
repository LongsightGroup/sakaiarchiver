package org.sakaiproject.util.archiver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
/**
 * Some utility methods to make parsing pages simpler.
 *
 * @author monroe
 */
public class ParsingUtils {

	public ParsingUtils() {
	}
	/**
	 * Find all elements of the specified type that use the specified css class.
	 *
	 * @param page  The page to search
	 * @param element The lowercase Html element, e.g. a, div, and the like.
	 * @param cssClass The css class to filter by.
	 * @return
	 */
	public static final List<?> findElementWithCssClass( HtmlPage page, String element, String cssClass ) {
		return page.getByXPath("//" + element + "[contains(@class,'" + cssClass + "')]");
	}
	/**
	 * Find all elements with the specified type attribute.  E.g. link with text/css.
	 *
	 * @param page  The page to search
	 * @param element The lowercase Html element, e.g. a, div, and the like.
	 * @param type The lowercased type to filter by.
	 * @return
	 */
	public static final List<?> findElementWithType( HtmlPage page, String element, String type ) {
		return page.getByXPath("//" + element + "[@type='" + type + "']");
	}
	/**
	 * Find all elements of the specified type.
	 *
	 * @param page  The page to search
	 * @param element The lowercase Html element, e.g. a, div, and the like.
	 * @return
	 */
	public static final  List<?> findAllElements( HtmlPage page, String element ) {
		return page.getByXPath("//" + element);
	}
	/**
	 * Find all the script statements that include a src attribute.
	 *
	 * @param page The page to search.
	 * @return
	 */
	public static final  List<?> findScriptElements( HtmlPage page ) {
		return page.getByXPath("//script[@src]");
	}
	/**
	 * Find all the img statements that include a src attribute.
	 *
	 * @param page The page to search.
	 * @return
	 */
	public static final  List<?> findImageElements( HtmlPage page ) {
		return page.getByXPath("//img[@src]");
	}
	/**
	 * Parse query string into map
	 *
	 * @param query The query string
	 * @return
	 */
	public static Map<String, String> getQueryMap(String query)	{
	    String[] params = query.split("&");
	    Map<String, String> map = new HashMap<String, String>();
	    for (String param : params) {
	        String[] parts = param.split("=");
//	        String name = parts[0];
	        String value = null;
	        if ( parts.length > 1 ) {
	            value = parts[1];
	        }
//else {
//    System.out.println("Missing query string value.  Name=" + parts[0]);
//    System.out.println("Missing query string value.  Query=" + query);
//}
	        map.put(parts[0], value);
	    }
	    return map;
	}
	/**
	 * Take a string of javascript and wrap it in the <Script... </Script> tags.
	 *
	 * @param script  The Javascript to wrap.
	 * @return The "Wrapped" Javascript.
	 */
	public static String addInlineJavaScript( String script ) {
        String js = "<script language=\"JavaScript\" type=\"text/javascript\">\r\n";
        js += script;
        js +="\r\n</script>";
	    return js;
	}
    /**
     * Create a <Script.. src=..... include string.
     *
     * @param file The javascript file to include.
     * @return The Javascript include string.
     */
    public static String addJavaScriptInclude( String file ) {
        String js = "<script src=\"" + file + "\" " +
            "type=\"text/javascript\" language=\"JavaScript\">" +
            "</script>\r\n";
        return js;
    }
}
