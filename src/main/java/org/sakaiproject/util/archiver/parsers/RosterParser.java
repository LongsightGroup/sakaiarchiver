package org.sakaiproject.util.archiver.parsers;

import org.sakaiproject.util.archiver.ToolParser;

public class RosterParser extends ToolParser {

	public static final String TOOL_NAME = "roster";

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

}
