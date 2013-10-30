package org.sakaiproject.util.archiver.parsers;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.sakaiproject.util.archiver.Archiver;
import org.sakaiproject.util.archiver.PageSaver;
import org.sakaiproject.util.archiver.ParsingUtils;
import org.sakaiproject.util.archiver.ToolParser;

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlImage;
import com.gargoylesoftware.htmlunit.html.HtmlInlineFrame;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class AssignmentsParser extends ToolParser {

	public static final String TOOL_NAME = "assignments";

	private Map<String,String> urlUpdateMap;

	public AssignmentsParser() {
		super();
	}

	public AssignmentsParser(String mainURL) {
		super(mainURL);
	}
	@Override
	public void initialize() {
		setSubdirectory(getToolName());
	}

	@Override
	public void parse() throws Exception {
        super.parse();
        HtmlPage page = getCurrentPage();
        setParentPage(page);

        // Get the main iframe
        List<?> elements = ParsingUtils.findElementWithCssClass(page, "iframe", "portletMainIframe");
        String path = ((HtmlInlineFrame) elements.get(0)).getSrcAttribute();
        HtmlPage assignments = getArchiver().getWebClient().getPage(path);

        Map<String,String> subPages = parseAssignmentPages(assignments);

        setUrlUpdateMap(subPages);

        // Overwrite the default frame with the "opened" version.
        String name = getSubdirectory() + FilenameUtils.getName(new URI(path).getPath());
        msg("Updating main assignments iframe: " + name + "(" +
                assignments.getTitleText()+")", Archiver.NORMAL);
        savePage(assignments, name );
	}

	@Override
    public void savePage(HtmlPage page, String filepath) throws IOException {
        PageSaver pageSaver = new PageSaver(getArchiver());
        pageSaver.setParser(this);
        pageSaver.save(page, filepath);
    }

    @Override
    public String modifySavedHtml(HtmlPage page, String html) {
        Map<String,String> urlMap = getUrlUpdateMap();
        if ( urlMap == null ) {
            return html;
        }
        String newHtml = html;

        // Update links
        for( String localPath: urlMap.keySet() ) {
            // Don't need full path.
            String href = urlMap.get(localPath).replaceAll("[?]","[?]").replaceAll("&","&amp;");
            localPath = FilenameUtils.getName(localPath);
            String pattern = PageSaver.HREF_REGEX + href + "\"";
            String replace = "class=\"offline-link\" href=\"" + localPath + "\"";
msg("pattern='" + pattern + "'", Archiver.DEBUG);
msg("replace=" + replace, Archiver.DEBUG);
            newHtml = newHtml.replaceAll(pattern, replace);
        }

        return newHtml;
    }
	/**
	 * Find the assignment links and get related pages.
	 *
	 * @param assignments
	 * @return A map object with localpath as key and original url as value.
	 *         Can be empty map.
	 * @throws URISyntaxException
	 * @throws IOException
	 */
	public Map<String,String> parseAssignmentPages(HtmlPage assignments)
	        throws URISyntaxException, IOException {

        Map<String,String> subPages = new HashMap<String,String>();

        List<HtmlAnchor> links = assignments.getAnchors();
        for( HtmlAnchor link: links ) {
            String href = link.getHrefAttribute().trim();
            if ( href.equals("") || href.startsWith("#") ||
                 href.startsWith("javascript:")) {
                continue;
            }
            URI hrefURI = new URI(href);
            String query  = hrefURI.getQuery();
            if ( query != null ) {
                Map<String,String> params = ParsingUtils.getQueryMap(query);
                String id = params.get("assignmentId");
                String action = params.get("sakai_action");
                if ( id == null || ! action.equals("doView_assignment")  ) {
                    continue;
                }
                String localPath = getSubdirectory() +
                        FilenameUtils.getName(id);
                subPages.put(localPath, href);

                if ( ! getArchiver().getSavedPages().contains(localPath)) {
                    HtmlPage assignmentPage = link.click();
                    assignmentPage = openStudentView( assignmentPage );

                    msg("Saving assignment page: " + localPath + "(" +
                            link.asText() + ")", Archiver.NORMAL);
                    PageSaver saver = new PageSaver(getArchiver());
                    saver.save(assignmentPage, localPath );
                    getArchiver().getSavedPages().add(localPath);
                }
            }
        }
	    return subPages;
	}
	/**
	 * Find the student view link, press it, and return the new page.
	 *
	 * Depends on expand.gif image.
	 *
	 * @param assignment
	 * @return The expanded page.
	 * @throws IOException
	 */
	public HtmlPage openStudentView(HtmlPage assignment ) throws IOException {
	    List<?> images = assignment.getByXPath("//img[contains(@src,'expand.gif')]");
	    if ( images == null || images.size() == 0 ) {
	        msg("Could not find student view expand link.", Archiver.WARNING);
	        return assignment;
	    }
	    HtmlImage expand = (HtmlImage) images.get(0);
	    HtmlAnchor link = (HtmlAnchor) expand.getParentNode();
	    HtmlPage page = link.click();
	    return page;
	}

	@Override
	public String getToolName() {
		return TOOL_NAME;
	}

    public Map<String, String> getUrlUpdateMap() {
        return urlUpdateMap;
    }

    public void setUrlUpdateMap(Map<String, String> urlUpdateMap) {
        this.urlUpdateMap = urlUpdateMap;
    }
}
