package org.sakaiproject.util.archiver.parsers;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.sakaiproject.util.archiver.Archiver;
import org.sakaiproject.util.archiver.ParsingUtils;
import org.sakaiproject.util.archiver.ToolParser;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTable;

public class ForumsParser extends ToolParser {

    public static final int MAIN_PAGE = 1;
    public static final int TOPIC_PAGE = 2;
    public static final int THREAD_PAGE = 3;

	public static final String TOOL_NAME = "forums";

	/** Helper string for searching forum onclick information */
    public static final String onClickPrefix =
            ".*document\\.forms\\[\\'msgForum\\'\\]\\[\\'";


	public ForumsParser() {
		super();
	}

	public ForumsParser(String mainURL) {
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

        parseMainPage(mainPage);

        // Forums maintains state on server, need to reset to get back to main page.
        mainPage = resetTool();
        // Overwrite the default frame with the updated version.
        String name = getSubdirectory() + getToolPageName();
        msg("Updating main roster iframe: " + name + "(" +
                mainPage.getTitleText()+")", Archiver.NORMAL);
        savePage(MAIN_PAGE, mainPage, name);
	}
	/**
	 * Parse the List of Forums page
	 *
	 * @param page
	 * @throws Exception
	 */
	public void parseMainPage( HtmlPage page ) throws Exception {

	    // Get all topic link ids.
	    HtmlTable table = page.getHtmlElementById("msgForum:forums");
	    List<HtmlAnchor> anchors = table.getHtmlElementsByTagName("a");
	    Map<String,String> urlChanges = new HashMap<String,String>();
	    List<String> topicIds = new ArrayList<String>();
	    for( HtmlAnchor anchor: anchors ) {
	        String id = anchor.getId();
	        if ( id != null &&
	             id.matches("msgForum[:]forums[:]\\d+[:]topics[:]\\d+[:]topic_title")) {
	            topicIds.add(id);
	        }
	    }

	    // Load each topic page and process it.
	    for( String id: topicIds ) {
	        page = resetTool();  // Tool main
	        HtmlAnchor anchor = page.getHtmlElementById(id);
            String onClick = anchor.getOnClickAttribute();
            //document.forms['msgForum']['forumId'].value='5728';
            String forumId = onClick.replaceAll(onClickPrefix +
                    "forumId\\'\\]\\.value\\=\\'(\\d+)\\'.*", "$1");
            //document.forms['msgForum']['topicId'].value='22569';
            String topicId = onClick.replaceAll(onClickPrefix +
                    "topicId\\'\\]\\.value\\=\\'(\\d+)\\'.*", "$1");

            page = anchor.click(); // Topic main
            parseThread( page, id, forumId, topicId );

            String name = getToolPageName() + "-forum-" + forumId +
                    "-topic-" + topicId;
            savePage(TOPIC_PAGE, page, getSubdirectory() + name);
            urlChanges.put(id, name);
	    }
	    getPageUrlUpdates().put("MAIN_PAGE", urlChanges);
	}

	public void parseThread( HtmlPage page, String topicLinkId,
	                         String forumId, String topicId ) throws Exception {
        // Get all thread link ids.
        HtmlTable table;
        try {
            table = page.getHtmlElementById("msgForum:messagesInHierDataTable");
        } catch ( ElementNotFoundException e ) {
            return; // No messages found
        }

        List<HtmlAnchor> anchors = table.getHtmlElementsByTagName("a");
        Map<String,String> urlChanges = new HashMap<String,String>();
        List<Integer> threadIds = new ArrayList<Integer>();
        int index = 0;
        for( HtmlAnchor anchor: anchors ) {
            String onClick = anchor.getOnClickAttribute();
            //document.forms['msgForum']['messageId'].value='60772';
            if ( onClick != null &&
                 onClick.matches(".*document\\.forms\\[\\'msgForum\\'\\]\\[\\'messageId\\'\\]\\.value\\=\\'.*")) {
                threadIds.add(new Integer(index));
            }
            index++;
        }

        // Load each thread page and process it.
        for( Integer anchorIndex: threadIds ) {
            page = resetTool();  // Main page
            HtmlAnchor anchor = page.getHtmlElementById(topicLinkId);
            String onClick = anchor.getOnClickAttribute();
            page = anchor.click();  // Topic page

            table = page.getHtmlElementById("msgForum:messagesInHierDataTable");
            anchors = table.getHtmlElementsByTagName("a");
            anchor = anchors.get(anchorIndex.intValue());

            onClick = anchor.getOnClickAttribute();

            //document.forms['msgForum']['messageId'].value='60772';
            String threadId = onClick.replaceAll(onClickPrefix +
                    "messageId\\'\\]\\.value\\=\\'(\\d+)\\'.*", "$1");

            page = anchor.click();  // Thread page

            String name = getToolPageName() + "-forum-" + forumId +
                    "-topic-" + topicId + "-thread-" + threadId;
            savePage(THREAD_PAGE, page, getSubdirectory() + name);
            urlChanges.put(threadId, name);
        }
        getPageUrlUpdates().put("TOPIC_PAGE", urlChanges);

	}

    @Override
    public String modifySavedHtml(HtmlPage page, String html) {
        String newHtml = html;
        Map<String,String> urlChanges;
        switch ( getPageSaveType() ) {
            case MAIN_PAGE:
                urlChanges = getPageUrlUpdates().get("MAIN_PAGE");
                newHtml = ParsingUtils.replaceMatchingAnchors( newHtml, urlChanges,
                        "[<]a\\s+id\\s*=\\s*[\"']\\s*", "\\s*[\"'][^>]*[>]");
                break;
            case TOPIC_PAGE:
                urlChanges = getPageUrlUpdates().get("TOPIC_PAGE");
                if (urlChanges != null ) {
                    newHtml = ParsingUtils.replaceMatchingAnchors(newHtml, urlChanges,
                        "[<]a\\s*[^>]*" + onClickPrefix +
                        "messageId\\'\\]\\.value\\=\\'",
                        "\\'[^>]*[>]");
                }
                break;
        }
        return newHtml;
    }

    @Override
    public String addJavascript() throws Exception {
        String js = "";
        String script;
        switch ( getPageSaveType() ) {
            case MAIN_PAGE:
            case TOPIC_PAGE:
                script = "\\$(document).ready(function() { "
                        + "\\$('DIV.toggle').css('display', '');});";
                js = ParsingUtils.addInlineJavaScript(script);
                break;
        }
        return js;
    }

	@Override
	public String getToolName() {
		return TOOL_NAME;
	}

}
