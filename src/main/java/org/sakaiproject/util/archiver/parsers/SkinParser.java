package org.sakaiproject.util.archiver.parsers;

import org.sakaiproject.util.archiver.ToolParser;

public class SkinParser extends ToolParser {

	public static final String TOOL_NAME = "skin";

	public SkinParser() {
		super();
	}

	public SkinParser(String mainURL) {
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
