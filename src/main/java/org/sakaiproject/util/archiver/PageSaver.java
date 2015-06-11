package org.sakaiproject.util.archiver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.BaseFrameElement;
import com.gargoylesoftware.htmlunit.html.FrameWindow;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlImage;
import com.gargoylesoftware.htmlunit.html.HtmlLink;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlScript;
/**
 * <p>Save HtmlPage with shared css, javascript, files, iframes, and other related
 * information.  Will parse out any embedded CSS images and download them
 * as well.  Optimized to check if file has already been downloaded via the
 * Archiver.getSavedPages() List.</p>
 *
 * <p>The base directory for saved pages is defined by the Archiver options
 * properties file archive.dir.base property.</p>
 *
 * </p>Binary files are determined based on either location in the resources
 * space (url contains /access/content) or if the extension is in the
 * binary.file.extensions property.</p>
 *
 * <p>Files referenced relative the the "root" will be relative to the base
 * directory. Files relative to the page will be in the page's directory.</p>
 *
 * <p>Links will be adjusted to point to these new locations</p>
 *
 * @author monroe
 */
public class PageSaver {

	public static final String SRC_REGEX = "src[\\s]*=[\\s]*\"";
	public static final String HREF_REGEX = "href[\\s]*=[\\s]*\"";

	private Archiver archiver;
	private HtmlPage page;
	private ToolParser parser;

	public PageSaver(Archiver archiver) {
		setArchiver(archiver);
	}
	/**
	 * Save the page with Css, Javascript, Files, and iframes.
	 *
	 * @param page The HTML page to save
	 * @param filepath  The file path and file name relative to the archive base path.
	 * @throws IOException
	 */
	public void save( HtmlPage page, String filepath ) throws Exception {
		setPage(page);

		File base = new File(getArchiver().getBasePath());
		File pageFile = new File(base, filepath);
		pageFile.getParentFile().mkdirs();
		String relativeRoot = base.toURI().relativize(pageFile.toURI()).getPath();
		relativeRoot = "../";  //TODO: Calculate this

		Map<String,String> cssFiles = parseCss( page );
		Map<String,String> jsFiles = parseJavascript(page);
		Map<String,String> imgFiles = parseImages( page, filepath );
		Map<String,String> files = parseFiles( page, filepath );
		Map<String,String> iframeFiles = parseIframes(page, filepath);

		String html = page.getWebResponse().getContentAsString();

		// Let the parser modify the html before any other changes.
        ToolParser parser = getParser();
		if ( parser != null ) {
	        html = parser.modifySavedHtml(page, html);
	    }

		// Update css links
		for( String cssFile: cssFiles.keySet() ) {
			String pattern = HREF_REGEX + cssFiles.get(cssFile).replaceAll("[?]", "[?]") + "\"";
			String replace = "href=\"" + relativeRoot + cssFile + "\"";
			html = html.replaceAll(pattern, replace);
		}
		// Update javascript links
		for( String newPath: jsFiles.keySet() ) {
			String orgPath = jsFiles.get(newPath);
			String pattern = SRC_REGEX + orgPath.replaceAll("[?]", "[?]") +"\"";
			String replace;
			if ( orgPath.startsWith("/") || orgPath.startsWith("http")) {
				replace = "src=\"" + relativeRoot + newPath + "\"";
			}
			else {
				replace = "src=\"" + newPath + "\"";
			}
			html = html.replaceAll(pattern, replace);
		}
		// Update image links
		for( String newPath: imgFiles.keySet() ) {
			String orgPath = imgFiles.get(newPath);
			String pattern = SRC_REGEX + orgPath.replaceAll("[?]", "[?]") +"\"";
			String replace;
			if ( orgPath.startsWith("/") || orgPath.startsWith("http")) {
				replace = "src=\"" + relativeRoot + newPath + "\"";
			}
			else {
				replace = "src=\"" + newPath + "\"";
			}
			html = html.replaceAll(pattern, replace);
		}
		// Update file links
		for( String newPath: files.keySet() ) {
			String orgPath = files.get(newPath);
			String pattern = HREF_REGEX + orgPath.replaceAll("[?]", "[?]") +"\"";
			String replace;
			if ( orgPath.startsWith("/") || orgPath.startsWith("http")) {
				replace = "href=\"" + relativeRoot + newPath + "\"";
			}
			else {
				replace = "href=\"" + newPath + "\"";
			}
			html = html.replaceAll(pattern, replace);
		}
		// Update iframe links
		for( String iframeFile: iframeFiles.keySet() ) {
			String frameURL = iframeFiles.get(iframeFile);
			frameURL = frameURL.replaceAll("[?]", "[?]");
			String pattern = SRC_REGEX + frameURL +"\"";
			String replace = "src=\"" + iframeFile + "\"";
			html = html.replaceAll(pattern, replace);
		}

		// Add offline js and css
		String replace = "$1";
		String script = "var siteHost = '" + getArchiver().getSiteHost() + "';";
		replace += ParsingUtils.addInlineJavaScript(script);
		// Check if JQuery loaded by page.
		if ( ! html.contains("library/js/jquery.js")  && ! html.contains("\"js/jquery-1.")) {
			replace +=  ParsingUtils.addJavaScriptInclude("../library/js/jquery.js");
		}
		replace += ParsingUtils.addJavaScriptInclude("../sakai-offline.js") +
		  "<link href=\"../sakai-offline.css\" type=\"text/css\" rel=\"stylesheet\" media=\"all\">\r\n";
        if ( parser != null ) {
            replace += parser.addJavascript();
        }
	    replace += "</body>$2";
		html = html.replaceAll("(?si)^(.*)</body>(.*)$",replace);

		saveContentString(html, filepath);
	}
	/**
	 * Find any anchors that reference files and not other pages.
	 *
	 * @param page
     * @param filepath The file path and file name relative to the archive base path.
	 * @return A link update map with local path as key and URL as value.
	 * @throws IOException
	 */
	public Map<String,String> parseFiles( HtmlPage page, String filepath ) throws IOException {
		Map<String,String> files = new HashMap<String,String>();
		File base = new File(getArchiver().getBasePath());
		File pageRoot = new File(base, filepath).getParentFile();

		List<HtmlAnchor> anchors = page.getAnchors();
        for( HtmlAnchor anchor: anchors ) {
        	String href = anchor.getHrefAttribute().trim();
        	// Skip javascript, host only site links (e.g. sakaiproject.org), and anchor links
        	if ( href.equals("") || href.startsWith("javascript:")  ||
        		href.matches("http[s]?://[^/]+") || href.startsWith("#") ) {
        		continue;
        	}
            String localPath = href.split("\\?")[0];  // Some images have query parameters.
            boolean relative = true;
            // Check if path is full url
            if ( href.startsWith("http")) {
            	URL url = new URL(href);
            	localPath = URLDecoder.decode(url.getPath(), "UTF-8");
            	relative = false;
            }
            if ( localPath.startsWith("/")) {
            	localPath = localPath.substring(1);
            	relative = false;
            }
        	String ext = FilenameUtils.getExtension(localPath);

            // cfm is Columbia specific link.
            if ( ext.equals("cfm")) {
                continue;
            }
            // Get all files in /access/content or any that match binary files
            // TODO: filter out external sites?
            if ( href.contains("/access/content") || getArchiver().getFileExtensions().contains(ext)) {
                if ( ! getArchiver().getSavedPages().contains(localPath)) {

               	    File file;
	               	if ( relative ) {
	               		file = new File(pageRoot, localPath );
	               	}
	               	else {
	               		file = new File(base, localPath);
	               	}
	               	file.getParentFile().mkdirs();
	               	msg("Saving file: " + href + "  localpath=" + file.getAbsolutePath(), Archiver.NORMAL);
	               	if ( ! Archiver.DEBUG_SKIP_FILES ) {
	               		InputStream in = null;
	               		OutputStream out = null;
	               		try {
	               			in = anchor.click().getWebResponse().getContentAsStream();
	               			out = new FileOutputStream(file);
	               			long size = IOUtils.copyLarge(in, out);
	            		    msg("File size: " + size, Archiver.NORMAL);
	               		} catch ( Exception e ) {
	               			e.printStackTrace();
	               		    localPath = "fileNotFound.htm?file=" + URLEncoder.encode(href, "UTF-8");
	               			msg("Could not download file: " + href, Archiver.WARNING);
	               		} finally {
		            	    	in.close();
		            		    out.close();
		                }
	               	}
	               	else {
	               		msg("DEBUG_SKIP_FILE is true, skipping file download", Archiver.WARNING);
	               	}
	            	getArchiver().getSavedPages().add(localPath);
                }
                files.put(localPath, href);
            }
        }  // For
		return files;
	}
	/**
	 * Parse any iframes contained in page and download them to the same dir as the main page.
	 *
	 * @param page The main page
	 * @param filepath The path to the main page's local file
	 * @return A map with the iframe name as key and URL to replace as value.
	 * @throws IOException
	 */
	public Map<String,String> parseIframes( HtmlPage page, String filepath ) throws Exception {
		Map<String,String> iframes = new HashMap<String,String>();

		List<FrameWindow> windows = page.getFrames();
		for(FrameWindow frame: windows ) {

		    // Skip iframes without source/javascript... probably CKeditor.
		    String src = frame.getFrameElement().getSrcAttribute();
		    if ( src.equals("") || src.startsWith("javascript:")) {
		        continue;
		    }

			HtmlPage framePage = (HtmlPage) frame.getEnclosedPage();
			BaseFrameElement element = frame.getFrameElement();
			String path = element.getSrcAttribute();
			String name = FilenameUtils.getName(new URL(path).getPath());
			// Map local name to full URL
			iframes.put(name, path);
			String pagePath = FilenameUtils.getPath(filepath);
            if ( ! getArchiver().getSavedPages().contains(path)) {
            	msg("Saving iframe: " + pagePath + name + "(" +
                    framePage.getTitleText()+")", Archiver.NORMAL);
            	PageSaver saver = new PageSaver(getArchiver());
            	saver.setParser(getParser());
            	saver.save(framePage, pagePath + name );
            	getArchiver().getSavedPages().add(path);
            }
		}
		return iframes;
	}
	/**
	 * Parse the img files in the HTML file and download them.
	 *
	 * @param page The html page
	 * @param filepath The filepath and name of the main page relative to the archive base.
	 * @return An array of src links to be replaced.
	 * @throws IOException
	 */
	public Map<String,String> parseImages( HtmlPage page, String filepath ) throws IOException {
		Map<String,String> imgFiles = new HashMap<String,String>();
		File base = new File(getArchiver().getBasePath());
		File pageRoot = new File(base, filepath).getParentFile();
		List<?> images = ParsingUtils.findImageElements(page);
        Iterator<?> it = images.iterator();
        while (it.hasNext()) {
            HtmlImage image = (HtmlImage) it.next();
            String path = image.getAttribute("src").trim();
            if (path == null || path.equals("")) continue;

            String localPath = path.split("\\?")[0];  // Some images have query parameters.
            boolean relative = true;
            // Check if path is full url
            if ( path.startsWith("http")) {
            	URL url = new URL(path);
            	localPath = URLDecoder.decode(url.getPath(), "UTF-8");
            	relative = false;
            }

            if ( localPath.startsWith("/")) {
            	localPath = localPath.substring(1);
            	relative = false;
            }
            imgFiles.put(localPath, path);
            if ( ! getArchiver().getSavedPages().contains(localPath)) {
				msg("Saving image: src path: " + path + "  local path: " +
                    localPath, Archiver.VERBOSE);
            	File imageFile;
            	if ( relative ) {
            		imageFile = new File(pageRoot, localPath );
            	}
            	else {
            		imageFile = new File(base, localPath);
            	}
            	imageFile.getParentFile().mkdirs();
            	try {
            	    image.saveAs(imageFile);
            	} catch (IOException e ) {
            	    localPath = "image-not-found.jpg?file=" +
            	            URLEncoder.encode(path,"UTF-8");
            	    msg("Could not save image: " + path + " Error was: '" +
            	            e.getMessage() + "'", Archiver.WARNING);
            	}
	            getArchiver().getSavedPages().add(localPath);
            }
        }
		return imgFiles;
	}
	/**
	 * Parse the css files from the HTML source and download them.
	 *
	 * @param page The HTML page
	 * @return An array of CSS hrefs to replace.
	 * @throws IOException
	 */
	public Map<String,String> parseCss( HtmlPage page ) throws IOException {
		Map<String,String> cssFiles = new HashMap<String,String>();
		List<?> links = ParsingUtils.findElementWithType(page, "link", "text/css");
        Iterator<?> it = links.iterator();
        while (it.hasNext()) {
            HtmlLink link = (HtmlLink) it.next();

            String path = link.getAttribute("href").trim();
            if (path == null || path.equals("")) {
            	continue;
            }
            URL cssUrl = page.getFullyQualifiedUrl(path);

            String localPath = path;
            if ( localPath.startsWith("/")) {
            	localPath = localPath.substring(1);
            }
            cssFiles.put(localPath, path);
            if ( ! getArchiver().getSavedPages().contains(path)) {
				msg("Saving css file: src path: " + path + "  local path: " +
                    localPath, Archiver.VERBOSE);

	            WebResponse resp = link.getWebResponse(true);
	            String css = resp.getContentAsString();
	            saveContentString(css, path);
	            getArchiver().getSavedPages().add(path);
	            parseCssImages(css, path, cssUrl);
            }
        }
        return cssFiles;
	}
	/**
	 * Download images embedded in css via url(...) statements.
	 *
	 * @param css The CSS file as a string
	 * @param path The path to the CSS file
	 * @param cssUrl  The URL for the CSS page
	 * @throws IOException If malformed URL or other IO error.
	 */
	public void parseCssImages(String css, String path, URL cssUrl ) throws IOException {
		final Pattern p = Pattern.compile("url\\(['\"]?([^'\")]+)['\"]?\\)");
		Matcher m = p.matcher(css);
		while(m.find()) {
			String cssImage = m.group(1).trim();

			if ( cssImage.startsWith("data:") ||
			    getArchiver().getSavedPages().contains(cssImage) ) {
				continue;
			}
			URL imgUrl;
			if ( cssImage.startsWith("/")) {
				imgUrl = new URL(cssUrl.getProtocol() + "://" + cssUrl.getHost() + cssImage);
			}
			else {
				imgUrl = new URL(cssUrl, cssImage);
			}
			String localPath = imgUrl.getPath().substring(1);
            msg("Saving css image:  src path: " + cssImage + "  local path: " +
			    localPath, Archiver.VERBOSE);
			File localFile = new File(getArchiver().getBasePath() + localPath);
			localFile.getParentFile().mkdirs();
            try {
            	downloadImage(imgUrl, localFile );
            } catch ( IOException e ) {
            	// Some images may not exist.
            	msg("Could not download CSS image:  " + imgUrl.toString(), Archiver.WARNING);
            }
            getArchiver().getSavedPages().add(cssImage);
		}
		return;
	}
	/**
	 * Parse the JavaScript includes and download them.
	 *
	 * @param page
	 * @return An Map with key as localpath and value as original path
	 * @throws IOException
	 */
	public Map<String,String> parseJavascript( HtmlPage page ) throws IOException {
    	String localPath;
		Map<String,String> jsFiles = new HashMap<String,String>();
		List<?> scripts = ParsingUtils.findScriptElements(page);
        Iterator<?> it = scripts.iterator();
        while (it.hasNext()) {
            HtmlScript script = (HtmlScript) it.next();

            String path = script.getSrcAttribute().trim();
            // The //: seems to come from an ie initializer optional code.
            if (path == null || path.equals("") || path.equals("//:")) {
                continue;
            }

        	URL url = page.getFullyQualifiedUrl(path);
        	localPath = path.split("\\?")[0];
            if ( path.startsWith("/")) {
            	localPath = path.substring(1);
                jsFiles.put(localPath, path);
            }
            else {
                if ( getParser() != null ) {
                    jsFiles.put(localPath, path);
                    localPath = getParser().getSubdirectory() + localPath;
                }
                else {
                    msg("Tool specific Javascript found but parser was null!",
                            Archiver.WARNING);
                    path = "/" + path;
                    jsFiles.put(localPath, path);
                }
            }
            if ( ! getArchiver().getSavedPages().contains(path)) {
                msg("Saving Javascript:  src path: " + path + "  localPath: " +
                        localPath, Archiver.VERBOSE);
            	Page jsPage = getArchiver().getWebClient().getPage(url);
	            saveContentString(jsPage.getWebResponse().getContentAsString(), localPath);
	            getArchiver().getSavedPages().add(path);
            }
        }
        return jsFiles;
	}
	/**
	 * Save raw content to a file.
	 *
	 * @param content The content to write out.
	 * @param path The file path relative to the archive base path.
	 */
	public void saveContentString(String content, String path ) throws FileNotFoundException {
		File file = new File(getArchiver().getBasePath() + path );
		file.getParentFile().mkdirs();
		PrintWriter out = new PrintWriter(file);
		try {
			out.print(content);
		} finally {
			out.close();
		}
	}
	/**
	 * Download an image from a URL.
	 *
	 * @param url
	 * @param file
	 * @throws IOException
	 */
    public void downloadImage( URL url, File file ) throws IOException {

        WebClient webclient = getArchiver().getWebClient();

        final String accept = webclient.getBrowserVersion().getImgAcceptHeader();
        final WebRequest request = new WebRequest(url, accept);
        request.setAdditionalHeader("Referer", getPage().getUrl().toExternalForm());
        WebResponse imageWebResponse_ = webclient.loadWebResponse(request);
        final ImageInputStream iis = ImageIO.createImageInputStream(imageWebResponse_.getContentAsStream());
        final Iterator<ImageReader> iter = ImageIO.getImageReaders(iis);
        if (!iter.hasNext()) {
            iis.close();
            throw new IOException("No image detected in response");
        }
        ImageReader imageReader = iter.next();
        imageReader.setInput(iis);
        ImageIO.write(imageReader.read(0), imageReader.getFormatName(), file);
    }
	/**
	 * Output a message via Archiver's msg method.
	 *
	 * @param msg
	 */
	public void msg( String msg, int level ) {
		getArchiver().msg(msg, level);
	}
	public Archiver getArchiver() {
		return archiver;
	}
	public void setArchiver(Archiver archiver) {
		this.archiver = archiver;
	}
	public HtmlPage getPage() {
		return page;
	}
	public void setPage(HtmlPage page) {
		this.page = page;
	}
	/**
	 * Get the tool parser object.
	 * @return The tool parser object.. may be null.
	 */
    public ToolParser getParser() {
        return parser;
    }
    public void setParser(ToolParser parser) {
        this.parser = parser;
    }
}
