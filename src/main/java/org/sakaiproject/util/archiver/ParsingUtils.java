package org.sakaiproject.util.archiver;

import java.util.List;

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
}
