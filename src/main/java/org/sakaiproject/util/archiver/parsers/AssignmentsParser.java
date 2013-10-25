package org.sakaiproject.util.archiver.parsers;

import org.sakaiproject.util.archiver.ToolParser;

public class AssignmentsParser extends ToolParser {

	public static final String TOOL_NAME = "assignments";

	public AssignmentsParser() {
		super();
	}

	public AssignmentsParser(String mainURL) {
		super(mainURL);
	}
	@Override
	public void initialize() {
		setSubdirectory(getToolName());
	}

	@Override
	public void parse() throws Exception {
		// TODO Auto-generated method stub
		super.parse();
	}

	@Override
	public String getToolName() {
		return TOOL_NAME;
	}

}
