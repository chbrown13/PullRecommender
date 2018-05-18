package com.chbrown13.tool_rec;

import com.github.gumtreediff.actions.*;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.*;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.*;
import com.github.gumtreediff.matchers.heuristic.gt.*;
import com.github.gumtreediff.tree.*;
import com.jcabi.github.Pull;
import com.jcabi.github.RepoCommit;
import java.io.*;
import java.lang.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.json.*;
import javax.xml.parsers.*;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/**
 * The Utils class contains various helper methods and variables for tool-recommender-bot
 */
public class Utils {

	public static String MARKDOWN_LINK = "[{src}]({url})";
	public static String LINK_URL = "https://github.com/{user}/{repo}/blob/{sha}/{path}#L{line}";
	public static String RAW_URL = "https://raw.githubusercontent.com/{user}/{repo}/{sha}/{path}";
	private static String PULL_DIFF = "https://patch-diff.githubusercontent.com/raw/{user}/{repo}/pull/{id}.diff";
    private static String COMMIT_DIFF = "https://github.com/{user}/{repo}/commit/{id}.diff";
	public static String BASE_COMMENT = "Good job! The {desc} {tool} reported an error [1] used to be here, but you fixed it.{similar}Check out {link} for more information.\n\n\n[1] {fixed}";
	public static String SURVEY = "[How useful was this recommendation?](https://ncsu.qualtrics.com/jfe/form/SV_4JGXYBRyb3GeF5X?project={project}&pr={id})";
	private static String MVN_COMPILE = "mvn -f {dir}/pom.xml compile";
	private static String MVN_CLEAN = "mvn -f {dir}/pom.xml clean";
	private static String currentDir = System.getProperty("user.dir");
	private static boolean myTool = false;
	private static boolean xmlProfile = false;
	private static boolean xmlPluginMgmt = false;
	private static boolean xmlReporting = false;
	private static String projectName = "";
	private static String projectOwner = "";
	private static String username = "";
	private static String password = "";
	private static String diff = "";
	private static int fixLine = -1;
	private static int fixType = -1;

	/**
	 * Stores current user's Github login
	 *
	 * @param name   Github account username
	 */
	private static void setUsername(String name) {
		username = name;
	}

	/**
	 * Returns the GitHub username
	 */
	public static String getUsername() {
		return username;
	}

	/**
	 * Stores current user's Github password
	 *
	 * @param pass   Github account password
	 */
	private static void setPassword(String pass) {
		if (pass != null) {
			password = pass;
		} else {
			password = "";
		}
	}

	/**
	 * Returns the GitHub user password
	 */
	public static String getPassword() {
		return password;
	}

	/**
	 * Stores current project name.
	 *
	 * @param name   Github repository name
	 */
	public static void setProjectName(String name) {
		projectName = name;
	}

	/**
	 * Gets repository name for current project.
	 *
	 * @returns   Github project name
	 */
	public static String getProjectName() {
		return projectName;
	}

	/**
	 * Stores username of Github repository for current project.
	 *
	 * @param user   Username of repo owner
	 */
	public static void setProjectOwner(String user) {
		projectOwner = user;
	}
	
	/**
	 * Gets owner of Github repository for current project.
     *
	 * @return   Username of repo owner
	 */	
	public static String getProjectOwner() {
		return projectOwner;
	}

	/**
	 * Gets the current directory
	 * 
	 * @return   Path to current directory
	 */
	public static String getCurrentDir() {
		return currentDir;
	}

	/**
	 * Gets the Gumtree node of error reported by ErrorProne
	 * 
	 * @param root   root of the file AST tree
	 * @param loc    Character offset location of error
	 * @return       ITree node of error
	 */
	private static ITree getErrorNode(ITree root, int loc) {
		List<ITree> it = root.getDescendants();
		ITree temp = root;
		int i = 0;
		while (temp.getPos() <= loc) {
			i++;
			temp = it.get(i);
		}
		return temp;
	}

	/**
	 * Get the character offset of an error based on the reported line
	 * 
	 * @param error   Error reported by ErrorProne
	 * @param source  Filename of source code
	 * @return        character offset where error begins
	 */
	private static int getErrorOffset(Error error, String source) {
		String log = error.getLog();
		String prev = null;
		int loc;
		int chars = 0;
		String bug = null;
		for(String line: log.split("\n")) {
			if(line.contains("^")) {
				loc = line.indexOf("^");
				bug = prev.substring(loc);
				break;
			}
			prev = line;
		}
		File file = new File(source);
		try {
			int i = 0;
		    Scanner sc = new Scanner(file);
		    while (sc.hasNext()) {
				if (i < error.getLineNumber()-1) {
					chars += sc.nextLine().length(); //ignore
				} else {
					String str = sc.nextLine();
					if (str.contains(bug)) {
						chars += str.indexOf(bug);
						return chars;
					}
				}
				i++;
		    }
		    sc.close();
		} 
		catch (FileNotFoundException e) {
		    e.printStackTrace();
		}
		return 0;
	}

	/**
	 * Check if a node exists in the updated file
	 * 
	 * @param node   ITree to search for in destination file
	 * @param tree   Mapping between file versions
	 * @return       True if node is in new file, else false
	 */
	private static boolean searchNode(ITree node, ITree tree) {
		if (node.getParent() == null) {
			return false;
		}
		else {
			ITree parent = node.getParent();
			if (parent.getChildren().contains(node)) {
				return true;
			} else {
				return searchNode(node, parent);
			}
		}
	}

	/**
	 * Gets the line number based on character offset
	 * 
	 * @param pos   Position of node in source code
	 * @param f     Name of destination file
	 * @return      Line number of node
	 */
	private static int posToLine(int pos, String f) {
		File file = new File(f);
		int line = 0;
		int count = 0;
		try {
		    Scanner sc = new Scanner(file);
		    while (sc.hasNext()) {
				count += sc.nextLine().length();
				line += 1;
				if (count >= pos) {
					break;
				}
			}
		    sc.close();
		} 
		catch (FileNotFoundException e) {
		    e.printStackTrace();
		}
		fixLine = line - 1;
		return line - 1;
	}

	/**
	 * Parse changes in file to determine if a fix was made
	 * 
	 * @param base     Name of source file
	 * @param head     Name of destination file
	 * @param errorPos Character offset of error
	 * @return		   Changed line number
	 */
	private static int findFix(String base, String head, int errorPos) {
		Run.initGenerators();		
		JdtTreeGenerator jdt1 = new JdtTreeGenerator();
		JdtTreeGenerator jdt2 = new JdtTreeGenerator();
		ITree src = null;
		ITree dst = null;
		TreeContext tree1 = null;
		TreeContext tree2 = null;
		Matcher m = null;
		ActionGenerator g = null;
		boolean deleteOnly = true;
		try {
			tree1 = jdt1.generateFromFile(base);
			tree2 = jdt2.generateFromFile(head);
			src = Generators.getInstance().getTree(base).getRoot();
			dst = Generators.getInstance().getTree(head).getRoot();
			m = Matchers.getInstance().getMatcher(src, dst);
			m.match();
			g = new ActionGenerator(src, dst, m.getMappings());
			g.generate();
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
		MappingStore store = m.getMappings();		
		ITree errorNode = getErrorNode(tree1.getRoot(), errorPos);
		List<Action> actions = g.getActions();
		Action closestAction;
		try {
			closestAction = actions.get(0);			
		} catch (IndexOutOfBoundsException e) {
			return -1;
		}		
		for(Action a: actions) {
			if(!a.toString().startsWith("DEL")) {
				deleteOnly = false;
			}
			if(a.toString().endsWith("@Deprecated")) {
				return -1;			
			}
			int pos = a.getNode().getPos();
			if (Math.abs(errorNode.getPos() - pos) < Math.abs(errorNode.getPos() - closestAction.getNode().getPos())) {
				closestAction = a;
			}
		}
		if (deleteOnly) {
			return -1;
		}
		ITree temp = null;
		if (closestAction.toString().startsWith("DEL")) { //get closest sibling or parent
			int i = errorNode.positionInParent();
			while (i > 0) {
				i -= 1;
				temp = errorNode.getParent().getChildren().get(i);
				if (!searchNode(temp, dst)) {
					return -1;
				} 
			}
			fixType = 1;
		} else { //INS or UPD or other
			temp = closestAction.getNode();
			while (!searchNode(temp, dst)) {
				temp = temp.getParent();
			}
			fixType = 0;
		}
		if (temp == null) {
			return -1;
		}
		return posToLine(temp.getPos(), head);
	}
	
	/**
	 * Returns line number of code fix
	 * 
	 * @param id   ID of code change (PR number or commit hash)
	 * @param type Type of code change
	 * @param err  Error fixed by user
	 * @return     Line number of what is considered a fix or null if none
     */
	public static int getFix(String id, String type, Error error) {
		String url = null;
		if (type.equals(Recommender.PULL)) {
			url = PULL_DIFF.replace("{user}", projectOwner).replace("{repo}", projectName).replace("{id}", id);
		} else if (type.equals(Recommender.COMMIT)) {
			url = COMMIT_DIFF.replace("{user}", projectOwner).replace("{repo}", projectName).replace("{id}", id);
		} 
		int newLine = fixType;
		String[] wget = wget(url).split("\n");
		boolean found = false;
		for (String line: wget) {
			if (line.contains(error.getFileName())) {
				found = true;
				continue;
			} 
			else if (line.startsWith("@@") && line.endsWith("@@")) {
				//ignore
				continue;
			}
			if (found) {
				String l = line.substring(1).trim();
				if (error.getLog().contains(l) && !l.equals("")) {
					break;
				} 					
				newLine += 1;
			}
		}
		return newLine;
	}

	/**
	 * Downloads files in questions and determines if bug was actually fixed
	 * 
	 * @param error  Error in question
	 * @return       True if error prone bug was fixed, else false
	 */
	public static boolean isFix(Error error, String base, String head) {
		String link = RAW_URL.replace("{user}", projectOwner).replace("{repo}", projectName)
			.replace("{path}", error.getLocalFilePath());
		String baseFile = "base.java";
		String headFile = "head.java";
		String file1 = wget(link.replace("{sha}", base), baseFile);
		String file2 = wget(link.replace("{sha}", head), headFile);
		if (file1.equals(file2)) {
			return false;
		}
		int fix = findFix(baseFile, headFile, getErrorOffset(error, baseFile));
		return fix > 0;
	}

	/**
	 * Compiles the project to analyze code in the repository
	 * 
	 * @param dir   Local path to current version of repo
	 * @return      Output from the maven build
	 */
	public static String compile(String dir) {
		String output = "";
		BufferedReader br = null;
		String compile = MVN_COMPILE.replace("{dir}", dir);
		String clean = MVN_CLEAN.replace("{dir}", dir);
		try {
			try {
				System.out.println(clean);
				Process p1 = Runtime.getRuntime().exec(clean);
				p1.waitFor();
				System.out.println(compile);
				Process p2 = Runtime.getRuntime().exec(compile);	
				p2.waitFor();
				System.out.println("compiled " + dir);
				br = new BufferedReader(new InputStreamReader(p2.getErrorStream()));
			} catch (InterruptedException ie) {
				ie.printStackTrace();
				return null;
			}
			String line;
			while ((line = br.readLine()) != null) {
			    output += line + "\n";
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return output;
	}

	/**
	 * Modifies pom.xml file to add current tool plugin for maven build
	 * 
	 * @param file   Path to pom.xml file
	 * @param tool   Tool to analyze code
	 * @param writer New pom.xml file to write to
	 */
	public static void parseXML(String file, Tool tool, FileWriter writer) {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser saxParser = factory.newSAXParser();
			DefaultHandler handler = new DefaultHandler() {
				@Override
				public void startElement(String uri, String localName, String qName,
							Attributes attributes) throws SAXException {
					try {
						writer.write("<" + qName + ">");
						if(qName.equals("pluginManagement")) {
							xmlPluginMgmt = true;
						} else if (qName.equals("profiles")) {
							xmlProfile = true;
						} else if (qName.equals("reporting")) {
							xmlReporting = true;
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				@Override
				public void endElement(String uri, String localName,
					String qName) throws SAXException {
					try {
						if (qName.equals("plugins") && !myTool) {
							writer.write(tool.getPlugin());
							myTool = true;			
						} else if (qName.equals("project") && !myTool) {
							writer.write(String.join("\n", "<build>", "<plugins>", 
								tool.getPlugin(), "</plugins>", "</build>"));
							myTool = true;
						} else if (qName.equals("reporting")) {
							xmlReporting = false;
						}
						writer.write("</" + qName + ">");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				@Override
				public void characters(char ch[], int start, int length) throws SAXException {
					try {
						writer.write(new String(ch, start, length));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			};
			saxParser.parse(file, handler);
		} catch (FileNotFoundException fnf) {
			System.out.println("No pom.xml");
			try {
			writer.write(String.join("\n", "<project>", "<modelVersion>4.0.0</modelVersion>",
			"<groupId>com.chbrown13.rec_test</groupId>", "<artifactId>tool-recommender-bot-test</artifactId>",
			"<version>1</version>", "<build>", "<plugins>", tool.getPlugin(), "</plugins>", "</build>", "</project>"));
			} catch (IOException io) {
				io.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Updates to pom.xml file to include tool plugin
	 * 
	 * @param dir   Current directory of the project
	 * @param tool  Static analysis tool to analyze code
	 */
	private static void addToolPomPlugin(String dir, Tool tool) {
		try {
			String pom = String.join("/", dir, "pom.xml");
			File tempPom = new File(String.join("/", dir, "pom.temp"));
			myTool = false;
			FileWriter writer = new FileWriter(tempPom, false);
			parseXML(pom, tool, writer);
			writer.close();
			tempPom.renameTo(new File(pom));			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Get errors from software engineering tool
	 * 
	 * @param path   Current instance project path
	 * @param hash   Hash of GitHub change
	 * @param tool   Tool to recommend
	 * @return       List of errors reported from tool
	 */
	public static List<Error> getErrors(String path, String hash, Tool tool) {
		String log = null;
		List<Error> errors = null;
		try {
			addToolPomPlugin(path, tool);
			log = compile(path);
			errors = tool.parseOutput(log);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return errors;
	}

	/**
	 * Remove temp repo directories
	 */
	public static void cleanup() {
		try {
			String[] dirs = {projectName+"2"};
			for (String d: dirs) {
				Process p = Runtime.getRuntime().exec("rm -rf " + d);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}	
	}

	/**
	 * Utility method to get file contents from url and download file.
	 * 
	 * @param link URL of raw file to download
	 * @param file Output file name
	 * @return     String of file contents
	 */
	private static String wget(String link, String file) {
		String text = wget(link);
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(text);
        } catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return text;
	}

	/**
	 * Utility method to get file contents from url.
	 * 
	 * @param link URL of raw file
	 * @return     String of file contents
	 */
	private static String wget(String link) {
		String s = "";
		String out = "";
		try {
			URL url = new URL(link);
			InputStream in;
			try {
				in = url.openStream();
			} catch (FileNotFoundException e) {
				//File URL does not exist, possibly new file in PR
				in = new ByteArrayInputStream("".getBytes("UTF-8"));
			}
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			while ((s = br.readLine()) != null) {
				out += s + "\n";
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return out;
	}

	/**
	 * Changes the current working directory of the program
	 * 
	 * @param dir   Directory to change into
	 */
	public static void cd(String dir) throws FileNotFoundException {
		String cmd = "cd " + dir;
		try {
			Process p = Runtime.getRuntime().exec(cmd);		
			if(!dir.equals("..")) {
				currentDir += "/" + dir;
			} else {
				currentDir = currentDir.substring(0, currentDir.lastIndexOf("/"));
			}
		} catch (IOException e) {
			throw new FileNotFoundException("Invalid directory name "+dir);			
		}
	}

	/**
	 * Retrieve Github username and password. 
	 * Requires .github.creds file with username on the first line and password on the second line.
	 *
	 * @param filename   Name of credentials file
	 * @return           Array containing the Github username and password
	 */
	public static String[] getCredentials(String filename) {
		String[] creds = new String[2];
		File file = new File(filename);
		try {
			int i = 0;
		    Scanner sc = new Scanner(file);
		    while (sc.hasNext()) {
				creds[i] = sc.nextLine();
				i++;
		    }
		    sc.close();
		} 
		catch (FileNotFoundException e) {
		    e.printStackTrace();
		}
		if (filename.contains("github")) {
			setUsername(creds[0]);
			setPassword(creds[1]);
		}
		return creds;
	}
}
