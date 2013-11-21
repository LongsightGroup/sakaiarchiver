package org.sakaiproject.util.archiver.parsers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.sakaiproject.util.archiver.Archiver;
import org.sakaiproject.util.archiver.ParsingUtils;
import org.sakaiproject.util.archiver.ToolParser;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;

public class GradeBookParser extends ToolParser {

	public static final String TOOL_NAME = "gradebook";

	public static final int MAIN_PAGE = 1;
	public static final int ALL_GRADES = 2;
    public static final int COURSE_GRADES = 3;

	public GradeBookParser() {
		super();
	}

	public GradeBookParser(String mainURL) {
		super(mainURL);
	}
	@Override
	public void initialize() {
		setSubdirectory(getToolName());
	}
    @Override
    public void parse() throws Exception {
        super.parse();

        setToolPageName(FilenameUtils.getName(new URI(getToolURL()).getPath()));
        HtmlPage mainPage = loadToolMainPage();

        Map<String,String> urlChanges = new HashMap<String,String>();
        urlChanges.put("gbForm:_idJsp2", getToolPageName());
        urlChanges.put("gbForm:_idJsp6", getToolPageName() + "-allgrades");
        urlChanges.put("gbForm:_idJsp10", getToolPageName() + "-coursegrades");
        getPageUrlUpdates().put("MAIN_PAGE", urlChanges);

        parseAllGrades(mainPage);

        mainPage = loadToolMainPage();
        parseCourseGrades(mainPage);

        mainPage = loadToolMainPage();

        // Overwrite the default frame with the updated version.
        String name = getSubdirectory() + getToolPageName();

        msg("Updating main roster iframe: " + name + "(" +
                mainPage.getTitleText()+")", Archiver.NORMAL);
        savePage(MAIN_PAGE, mainPage, name);
    }
    /**
     * Parse the All Grades page
     *
     * @param page
     * @throws Exception
     */
    public void parseAllGrades(HtmlPage page) throws Exception  {

        HtmlAnchor anchor = page.getHtmlElementById("gbForm:_idJsp6");
        page = anchor.click(); // All grades page

        // Make sure show all is selected
        HtmlSelect select = page.getHtmlElementById("gbForm:pager_pageSize");
        HtmlOption option = select.getOptionByValue("0");
        page = select.setSelectedAttribute(option, true);

        parseAllGradesExcel( page );

        String name = getSubdirectory() + getToolPageName() + "-allgrades";
        savePage(ALL_GRADES, page, name);
    }
    /**
     * Parse the Course Grades page
     *
     * @param page
     * @throws Exception
     */
    public void parseCourseGrades(HtmlPage page) throws Exception  {

        HtmlAnchor anchor = page.getHtmlElementById("gbForm:_idJsp10");
        page = anchor.click();

        // Make sure show all is selected
        HtmlSelect select = page.getHtmlElementById("gbForm:pager_pageSize");
        HtmlOption option = select.getOptionByValue("0");
        page = select.setSelectedAttribute(option, true);

        String name = getSubdirectory() + getToolPageName() + "-coursegrades";
        savePage(ALL_GRADES, page, name);
    }
    /**
     * Get the All Grades Excel file
     *
     * @param page
     * @throws Exception
     */
    public void parseAllGradesExcel( HtmlPage page ) throws Exception {

        final String excelId = "gbForm:exportExcel";

        Map<String,String> urlChanges = new HashMap<String,String>();

        HtmlSubmitInput excelSubmit = page.getHtmlElementById(excelId);

        String filename = "gradebook-" + getArchiver().getSite() + ".xls";
        String localPath = getSubdirectory() + filename;

        File file = new File(getArchiver().getBasePath() + localPath);
        file.getParentFile().mkdirs();
        msg("Saving All Grades Excel file (please wait).", Archiver.NORMAL);
        Page filePage = null;
        try {
            filePage = excelSubmit.click();
        } catch ( Exception e ) {
            localPath = "fileNotFound.htm?file=" + filename;
            msg("Could not download Gradebook Excel file.",
                Archiver.ERROR);
        }
        if ( filePage != null ) {
            InputStream in = filePage.getWebResponse().getContentAsStream();
            OutputStream out = new FileOutputStream(file);
            try {
                long size = IOUtils.copyLarge(in, out);
                msg("File size: " + size, Archiver.NORMAL);
            } finally {
                out.close();
            }
        }
        urlChanges.put(excelId, filename);
        getPageUrlUpdates().put("ALL_GRADES", urlChanges);
    }

    @Override
    public String modifySavedHtml(HtmlPage page, String html) {
        String newHtml = html;
        Map<String,String> urlChanges = getPageUrlUpdates().get("MAIN_PAGE");
        switch ( getPageSaveType()) {
            case MAIN_PAGE:
            case COURSE_GRADES:
                newHtml =
                    ParsingUtils.replaceMatchingAnchorsById(newHtml, urlChanges);
                break;
            case ALL_GRADES:
                newHtml =
                    ParsingUtils.replaceMatchingAnchorsById(newHtml, urlChanges);

                // Should only be one id.
                urlChanges = getPageUrlUpdates().get("ALL_GRADES");
                for ( String id: urlChanges.keySet()) {
                    String url = urlChanges.get(id);
                    String regex = "[<]\\s*input\\s+[^>]*id\\s*=\\s*[\"']\\s*" +
                                    id + "\\s*[\"'][^>]*[>]";
                    String replacement = "<a class='offline-link' href='" + url
                                + "' style='float: right; margin-left: 15px;'>"
                                + "Export for Excel</a>";
                    newHtml = newHtml.replaceAll(regex, replacement);
                }
                break;
        }
        return newHtml;
    }

	@Override
	public String getToolName() {
		return TOOL_NAME;
	}
}
