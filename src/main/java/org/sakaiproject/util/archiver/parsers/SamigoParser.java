package org.sakaiproject.util.archiver.parsers;

import org.sakaiproject.util.archiver.ToolParser;

public class SamigoParser extends ToolParser {

	public static final String TOOL_NAME = "samigo";

	public SamigoParser() {
		super();
	}

	public SamigoParser(String mainURL) {
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
}
