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

	// View names / Page types
	public static final String MAIN_PAGE = "lisofass1";
	public static final String BY_STUDENT = "lisofass2";
	public static final String GRADE_PAGE = "grarep";
	public static final String STUDENT_VIEW = "stuvie";

	private String pageSaveType;
	private Map<String,Map<String,String>> pageUrlUpdates;

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
    public List<String> getResources() {
        List<String> resources = super.getResources();
        resources.add("sakai-offline-assignments.js");
        return resources;
    }

	@Override
	public void parse() throws Exception {
        super.parse();
        HtmlPage page = getCurrentPage();
        setParentPage(page);
        setToolPageName(FilenameUtils.getName(new URI(getToolURL()).getPath()));

        // Get the main iframe
        List<?> elements = ParsingUtils.findElementWithCssClass(page, "iframe", "portletMainIframe");
        String path = ((HtmlInlineFrame) elements.get(0)).getSrcAttribute();
        HtmlPage assignments = getArchiver().getWebClient().getPage(path);

        Map<String,String> subPages = parseAssignmentPages(assignments);

        getPageUrlUpdates().put(MAIN_PAGE,subPages);

        parseByStudentsView( assignments );

        parseGradePageView( assignments );

        parseStudentView(assignments);

        // Overwrite the default frame with the "opened" version.
        String name = getSubdirectory() + getToolPageName();
        msg("Updating main assignments iframe: " + name + "(" +
                assignments.getTitleText()+")", Archiver.NORMAL);
        setPageSaveType(MAIN_PAGE);
        savePage(assignments, name );
	}
    /**
     * Parse the Student View view
     *
     * @param page
     * @return
     * @throws IOException
     * @throws URISyntaxException
     */
    public Map<String,String> parseStudentView( HtmlPage page )
            throws Exception {
//TODO:  Handle paging (>200)
        HtmlPage view = getSubViewPage(page, STUDENT_VIEW);

        String name = FilenameUtils.getName(view.getUrl().getPath()) +
                "-" + STUDENT_VIEW;

        msg("Saving Students Assignment view page: " + name, Archiver.NORMAL);
        setPageSaveType(STUDENT_VIEW);
        savePage( view, getSubdirectory() + name);

        return null;
    }

    /**
     * Parse the Grade Page View
     *
     * @param page
     * @return
     * @throws IOException
     * @throws URISyntaxException
     */
    public Map<String,String> parseGradePageView( HtmlPage page )
            throws Exception {
//TODO:  Handle paging (>200)
        HtmlPage view = getSubViewPage(page, GRADE_PAGE);

        String name = FilenameUtils.getName(view.getUrl().getPath()) +
                "-" + GRADE_PAGE;

        msg("Saving Grade Report view page: " + name, Archiver.NORMAL);
        setPageSaveType(GRADE_PAGE);
        savePage( view, getSubdirectory() + name);

        return null;
    }

    /**
     * Parse the By Students View page
     *
     * @param page
     * @return
     * @throws IOException
     * @throws URISyntaxException
     */
    public Map<String,String> parseByStudentsView( HtmlPage page )
            throws Exception {
        HtmlPage view = getSubViewPage(page, BY_STUDENT);

        view = expandPage(view);

        Map<String,String> linkMap = parseStudentSubmissions( view );
        getPageUrlUpdates().put(BY_STUDENT, linkMap);

        String name = FilenameUtils.getName(view.getUrl().getPath()) +
                "-" + BY_STUDENT;

        msg("Saving By Students view page: " + name, Archiver.NORMAL);
        setPageSaveType(BY_STUDENT);
        savePage( view, getSubdirectory() + name);

        return null;
    }
    /**
     * Parses the Assignments by Student view to get the student submission
     * pages.
     *
     * @param page
     * @return The local to site URL map.
     * @throws IOException
     * @throws URISyntaxException
     */
    public Map<String,String> parseStudentSubmissions( HtmlPage page )
            throws Exception {
        Map<String,String> subPages = new HashMap<String,String>();

        List<HtmlAnchor> links = page.getAnchors();
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
                String id = params.get("submissionId");
                if ( id == null ) {
                    continue;
                }
                String localPath = getSubdirectory() + FilenameUtils.getName(id);
                subPages.put(localPath, href);

                if ( ! getArchiver().getSavedPages().contains(localPath)) {
                    HtmlPage submissionPage = link.click();
                    submissionPage = expandPage( submissionPage );

                    msg("Saving submission page: " + localPath + "(" +
                            link.asText() + ")", Archiver.NORMAL);
                    setPageSaveType("submission");
                    savePage(submissionPage, localPath);
                    getArchiver().getSavedPages().add(localPath);

                    resetTool();
                }
            }
        }
        return subPages;
    }

    @Override
    public void savePage(HtmlPage page, String filepath) throws Exception {
        PageSaver pageSaver = new PageSaver(getArchiver());
        pageSaver.setParser(this);
        pageSaver.save(page, filepath);
    }

    @Override
    public String modifySavedHtml(HtmlPage page, String html) {
        String saveType = getPageSaveType();
        Map<String,String> urlMap = getPageUrlUpdates().get(saveType);
        String newHtml = html;

        if  ( MAIN_PAGE.equals(saveType) ) {
            // Update links
            for( String localPath: urlMap.keySet() ) {
                String href = urlMap.get(localPath).replaceAll("[?]","[?]").replaceAll("&","&amp;");
                // Don't need full path.
                localPath = FilenameUtils.getName(localPath);
                String pattern = PageSaver.HREF_REGEX + href + "\"";
                String replace = "class=\"offline-link\" href=\"" + localPath + "\"";
                newHtml = newHtml.replaceAll(pattern, replace);
            }
        }
        if ( BY_STUDENT.equals(saveType)) {
            // Update links
            for( String localPath: urlMap.keySet() ) {
                String href = urlMap.get(localPath).replaceAll("[?]","[?]").replaceAll("&","(&amp;|&)");
                // Don't need full path.
                localPath = FilenameUtils.getName(localPath);
                String pattern = PageSaver.HREF_REGEX + href + "\"";
                String replace = "class=\"offline-link\" href=\"" + localPath + "\"";
                newHtml = newHtml.replaceAll(pattern, replace);
            }
        }

        // Update tool navigation links
        String toolPage = getToolPageName();

        // Secondary Assignment views navigation (viewing assignment)
        newHtml = newHtml.replaceAll(
            "<a href=\"#\" title = \"Assignment List\" ",
            "<a class=\"offline-link\" title=\"Assignment List\" href=\"" +
            toolPage + "\"");
        newHtml = newHtml.replaceAll(
                "<a href=\"#\" title=\"Grade Report\" ",
                "<a class=\"offline-link\" title=\"Grade Report\" href=\"" +
                toolPage + GRADE_PAGE + "\"");
        newHtml = newHtml.replaceAll(
                "<a href=\"#\" title=\"Student View\" ",
                "<a class=\"offline-link\" title=\"Student View\" href=\"" +
                toolPage + STUDENT_VIEW + "\"");

        // Main Assignment views navigation
        newHtml = newHtml.replaceAll("href=\".*view="+MAIN_PAGE+"[^\"]*\" ",
                "class=\"offline-link\" href=\"" + toolPage + "\"");
        newHtml = newHtml.replaceAll("href=\".*view="+GRADE_PAGE+"[^\"]*\" ",
                "class=\"offline-link\" href=\""+toolPage+"-"+GRADE_PAGE + "\"");
        newHtml = newHtml.replaceAll("href=\".*view="+STUDENT_VIEW+"[^\"]*\" ",
                "class=\"offline-link\" href=\""+toolPage+"-"+STUDENT_VIEW+"\"");

        return newHtml;
    }

    @Override
    public String addJavascript() throws URISyntaxException {

        String js = "";
        String saveType = getPageSaveType();
        if ( MAIN_PAGE.equals(saveType)  || BY_STUDENT.equals(saveType)) {
            String pageName = getToolPageName();
            js = "\\$(\"select#view\").hide().after(" +
                  "\"<span><a class='offline-link' href='" + pageName +
                  "'>Assignment List</a>&nbsp&nbsp;|&nbsp&nbsp;" +
                  "<a class='offline-link' href='" + pageName+"-"+BY_STUDENT +
                  "'>Assignment List by Student</a></span>\");";
            js = ParsingUtils.addInlineJavaScript(js);
        }
        js += ParsingUtils.addJavaScriptInclude("../sakai-offline-assignments.js");
        return js;
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
	        throws Exception {

//TODO: handle paging (>200)
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
                    setPageSaveType("assignment");
                    savePage(assignmentPage, localPath);
                    getArchiver().getSavedPages().add(localPath);

                    resetTool();
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
	public HtmlPage openStudentView( HtmlPage assignment ) throws IOException {
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
    /**
     * Takes a sakai page with "expand.gif" links, clicks on all of them, and
     * returns the expanded page.
     *
     * Depends on expand.gif image.
     *
     * @param assignment
     * @return The expanded page.
     * @throws IOException
     */
    public HtmlPage expandPage( HtmlPage page ) throws IOException {
        // Loop thru page invoking expansion ajax links until everything is opened.
        HtmlPage expandedPage = page;
        List<?> images = expandedPage.getByXPath("//img[contains(@src,'expand.gif')]");
        while ( images.size() > 0 ) {
            HtmlImage image = (HtmlImage) images.get(0);
            HtmlAnchor link = (HtmlAnchor) image.getParentNode();
            expandedPage = link.click();
            images = expandedPage.getByXPath("//img[contains(@src,'expand.gif')]");
        }
        return expandedPage;
    }

	@Override
	public String getToolName() {
		return TOOL_NAME;
	}

    public String getPageSaveType() {
        return pageSaveType;
    }

    public void setPageSaveType(String pageSaveType) {
        this.pageSaveType = pageSaveType;
    }
    /**
     * Get the page url map.
     *
     * @return Always returns an object.
     */
    public Map<String, Map<String,String>> getPageUrlUpdates() {
        if ( pageUrlUpdates == null ) {
            pageUrlUpdates = new HashMap<String,Map<String,String>>();
        }
        return pageUrlUpdates;
    }

    public void setPageUrlUpdates(Map<String, Map<String,String>> pageUrlUpdates) {
        this.pageUrlUpdates = pageUrlUpdates;
    }
}
