package org.sakaiproject.util.archiver.parsers;

import java.net.URL;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.sakaiproject.util.archiver.Archiver;
import org.sakaiproject.util.archiver.ParsingUtils;
import org.sakaiproject.util.archiver.ToolParser;

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlInlineFrame;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class ResourcesParser extends ToolParser {

	public static final String TOOL_NAME = "resources";

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
		HtmlPage page = getCurrentPage();
		setParentPage(page);
		List<?> elements = ParsingUtils.findElementWithCssClass(page, "iframe", "portletMainIframe");
		String path = ((HtmlInlineFrame) elements.get(0)).getSrcAttribute();
        HtmlPage resources = getArchiver().getWebClient().getPage(path);

        // Loop thru page invoking folder expansion ajax links until all folders opened.
        List<?> folders = resources.getByXPath("//a[@title='Open this folder']");
        while ( folders.size() > 0 ) {
        	HtmlAnchor link = (HtmlAnchor) folders.get(0);
        	resources = link.click();
        	folders = resources.getByXPath("//a[@title='Open this folder']");
        }

        // Overwrite the default frame with the "opened" version.
		String name = getSubdirectory() + FilenameUtils.getName(new URL(path).getPath());
       	msg("Updating resource iframe: " + name + "(" +
                resources.getTitleText()+")", Archiver.NORMAL);
       	savePage(resources, name );
	}
}
