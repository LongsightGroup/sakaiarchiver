package org.sakaiproject.util.archiver.parsers;

import java.net.URI;

import org.apache.commons.io.FilenameUtils;
import org.sakaiproject.util.archiver.Archiver;
import org.sakaiproject.util.archiver.ParsingUtils;
import org.sakaiproject.util.archiver.ToolParser;

import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class SyllabusParser extends ToolParser {

    public static final int MAIN_PAGE = 1;

	public static final String TOOL_NAME = "syllabus";

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

        // Overwrite the default frame with the updated version.
        String name = getSubdirectory() + getToolPageName();
        msg("Updating main roster iframe: " + name + "(" +
                mainPage.getTitleText()+")", Archiver.NORMAL);
        savePage(MAIN_PAGE, mainPage, name);
    }

    @Override
    public String addJavascript() throws Exception {
        String js = "";
        String script = "";
        switch ( getPageSaveType() ) {
            case MAIN_PAGE:
                script = "\\$(\"UL.navIntraTool\").after(\""
                    + "<div id='syllabusInfo'>Note: The links in the syllabus "
                    + "bars below are all click-able (even though they are not in "
                    + "red).  The edit options may open popups and appear to "
                    + "work, but they will permanenty change things.  A page "
                    + "reload will undo them.</div>\");";
                js = ParsingUtils.addInlineJavaScript(script);
                break;
        }
        return js;
    }
}
