package org.sakaiproject.util.archiver.parsers;

import org.sakaiproject.util.archiver.ToolParser;

public class HomeParser extends ToolParser {

	public static final String TOOL_NAME = "home";

	public HomeParser() {
		super();
	}

	public HomeParser(String mainURL) {
		super(mainURL);
	}
	@Override
	public void initialize() {
		setSubdirectory(getToolName());
	}

	@Override
	public String getToolName() {
		return "home";
	}

}
