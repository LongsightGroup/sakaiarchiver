package org.sakaiproject.util.archiver.parsers;

import org.sakaiproject.util.archiver.ToolParser;

public class ForumsParser extends ToolParser {

	public static final String TOOL_NAME = "forums";

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
		// TODO Auto-generated method stub
		super.parse();
	}
	@Override
	public String getToolName() {
		return TOOL_NAME;
	}


}
