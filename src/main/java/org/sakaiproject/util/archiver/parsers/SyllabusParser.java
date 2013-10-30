package org.sakaiproject.util.archiver.parsers;

import org.sakaiproject.util.archiver.ToolParser;

public class SyllabusParser extends ToolParser {

	public static final String TOOL_NAME = "syllabus";
	@Override
	public void initialize() {
		setSubdirectory(getToolName());
	}

	@Override
	public String getToolName() {
		return TOOL_NAME;
	}
}
