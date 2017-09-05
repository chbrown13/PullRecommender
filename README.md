# PullRecommender
Jenkins plugin to automatically recommend Google's Error Prone static analysis tool in pull requests for open source Java projects.

Run from source code:
```
$ sudo javac -cp jcabi-github-0.23-jar-with-dependencies.jar:error_prone_ant-2.1.0.jar com/chbrown13/pull_rec/ErrorProneItem.java com/chbrown13/pull_rec/PullRecommender.java com/chbrown13/pull_rec/Utils.java
$ java -cp .:jcabi-github-0.23-jar-with-dependencies.jar:error_prone_ant-2.1.0.jar:slf4j-nop-1.7.25.jar com.chbrown13.pull_rec.PullRecommender
```
