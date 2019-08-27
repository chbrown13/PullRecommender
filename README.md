# tool-recommender-bot
tool-recommender-bot is an automated recommendation system designed to increase awareness and adoption of software engineering tools among developers. The initial implementation automatically recommends Error Prone, an open source static analysis tool for Java code, to GitHub users.

Set Up:
* Dependencies:
	* Java
	* Maven
* Install required jar files: 
	* jcabi-github-0.38-jar-with-dependencies.jar
	* org.eclipse.jgit-4.9.0.201710071750-r.jar
	* snakeyaml-1.25.jar
	* slf4j.jar (optional)
* Create a .github.creds file with two lines, one that contains your github username and one with your password.


Run from source code:
```
$ sudo javac -cp jcabi-github-0.38-jar-with-dependencies.jar:org.eclipse.jgit-4.9.0.201710071750-r.jar:snakeyaml-1.25.jar com/chbrown13/tool_rec/ErrorProne.java com/chbrown13/tool_rec/Tool.java com/chbrown13/tool_rec/Error.java com/chbrown13/tool_rec/Recommender.java com/chbrown13/tool_rec/Utils.java
$ java -cp .:jcabi-github-0.38-jar-with-dependencies.jar:org.eclipse.jgit-4.9.0.201710071750-r.jar:snakeyaml-1.25.jar com.chbrown13.tool_rec.Recommender
```
