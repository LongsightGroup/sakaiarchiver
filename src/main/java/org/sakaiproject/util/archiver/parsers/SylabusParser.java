package org.sakaiproject.util.archiver.parsers;

import org.sakaiproject.util.archiver.ToolParser;

public class SylabusParser extends ToolParser {

	public static final String TOOL_NAME = "sylabus";
	@Override
	public void initialize() {
		setSubdirectory(getToolName());
	}

	@Override
	public String getToolName() {
		return TOOL_NAME;
	}
}
