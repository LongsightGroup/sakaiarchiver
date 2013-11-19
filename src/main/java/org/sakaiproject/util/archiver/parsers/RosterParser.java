package org.sakaiproject.util.archiver.parsers;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.sakaiproject.util.archiver.Archiver;
import org.sakaiproject.util.archiver.ParsingUtils;
import org.sakaiproject.util.archiver.ToolParser;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlImage;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTable;

/**
 * <p>Parse the Sakai Roster tool.</p>
 *
 * <p>Note that Student picture download can be enabled or disabled using
 * the Sakai Archiver property, download.student.pictures.  Sometime
 * offline student images can be against school or goverment regulations.</p>
 *
 * @author monroe
 *
 */
public class RosterParser extends ToolParser {

	public static final String TOOL_NAME = "roster";
	public static final int MAIN_PAGE = 1;
	public static final int OVERVIEW_PAGE = 2;
	public static final int GROUPS_PAGE = 3;

	public RosterParser() {
		super();
	}

	public RosterParser(String mainURL) {
		super(mainURL);
	}
	@Override
	public void initialize() {
		setSubdirectory(getToolName());
	}

	@Override
	public String getToolName() {
		return TOOL_NAME;
	}

    @Override
    public void parse() throws Exception {
        super.parse();

        setToolPageName(FilenameUtils.getName(new URI(getToolURL()).getPath()));
        HtmlPage mainPage = loadToolMainPage();

        parseMainPage(mainPage);
        parseOverviewPage(mainPage);

        mainPage = loadToolMainPage();
        parseGroupsPage(mainPage);

        mainPage = loadToolMainPage();
        // Overwrite the default frame with the updated version.
        String name = getSubdirectory() + getToolPageName();
        msg("Updating main roster iframe: " + name + "(" +
                mainPage.getTitleText()+")", Archiver.NORMAL);
        savePage(MAIN_PAGE, mainPage, name);
    }
    /**
     * Parse the groups page ( if it exists)
     *
     * @param page
     * @throws Exception
     */
    public void parseGroupsPage( HtmlPage page ) throws Exception  {
        HtmlAnchor link;
        try {
            link = page.getHtmlElementById("roster_form:_idJsp5");
        } catch ( ElementNotFoundException e ) {
            msg("Roster has no groups page", Archiver.NORMAL);
            return;
        }
        page = link.click();

        String name = getSubdirectory() + getToolPageName() + "-groups";
        savePage(OVERVIEW_PAGE, page, name);
    }
    /**
     * Parse the roster overview page
     *
     * @param page
     * @throws Exception
     */
    public void parseOverviewPage( HtmlPage page ) throws Exception {
        HtmlAnchor link = page.getHtmlElementById("roster_form:_idJsp4");
        page = link.click();

        String name = getSubdirectory() + getToolPageName() + "-overview";
        savePage(GROUPS_PAGE, page, name);
    }
    /**
     * Parse the facebook page.
     *
     * @param page
     * @throws IOException
     */
    public void parseMainPage(HtmlPage page ) throws IOException {
        // Get the student images.
        HtmlTable table = (HtmlTable)
                ParsingUtils.findElementWithCssClass( page, "table",
                                                    "rosterPictures").get(0);
        List<?> images = table.getByXPath("//img");
        Map<String,String> urlChanges = new HashMap<String,String>();
        boolean imagesAllowed = Boolean.parseBoolean(
                getArchiver().getOption(Archiver.DOWNLOAD_STUDENT_PICTURES));
        for( Object obj: images ) {
            HtmlImage img = (HtmlImage) obj;
            String src = img.getSrcAttribute();
            if ( ! src.startsWith("ParticipantImageServlet") ) {
                continue;
            }
            String localPath;
            if ( imagesAllowed ) {
                Map<String,String> query =
                        ParsingUtils.getQueryMap(src.split("\\?")[1]);
                String name = query.get("photo") + ".jpg";
                File file = new File(getArchiver().getBasePath() +
                                            getSubdirectory() + name );
                file.getParentFile().mkdirs();
                img.saveAs(file);
                localPath = name;
            }
            else {
                localPath = "../not-available-photo.png";
            }
            urlChanges.put(src,localPath);
        }
        getPageUrlUpdates().put("MAIN_PAGE", urlChanges);

        // Default image handler creates the first student's image
        // Delete for security reasons.
        File defaultImg = new File(getArchiver().getBasePath() +
                    getSubdirectory() + "ParticipantImageServlet.prf");
        defaultImg.delete();
    }

    @Override
    public String modifySavedHtml(HtmlPage page, String html) {
        Map<String,String> urlChanges;
        String newHtml = html;
        // Update the tool nav links.
        Map<String,String> toolNav = new HashMap<String,String>();
        toolNav.put("roster_form:_idJsp3", getToolPageName());
        toolNav.put("roster_form:_idJsp4", getToolPageName() + "-overview");
        toolNav.put("roster_form:_idJsp5", getToolPageName() + "-groups");

        switch ( getPageSaveType() ) {
            case MAIN_PAGE:
                urlChanges = getPageUrlUpdates().get("MAIN_PAGE");
                for ( String url: urlChanges.keySet()) {
                    String local = urlChanges.get(url);
                    newHtml = newHtml.replaceAll(Pattern.quote(url), local);
                }
                // Update tool nav for both pages.
            case OVERVIEW_PAGE:
            case GROUPS_PAGE:
                newHtml = ParsingUtils.replaceMatchingAnchors( newHtml, toolNav,
                        "[<]a\\s+[^>]*id\\s*=\\s*[\"']\\s*", "\\s*[\"'][^>]*[>]");
                break;
        }
        return newHtml;
    }
}
