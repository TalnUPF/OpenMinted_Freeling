#!/bin/bash
export LD_LIBRARY_PATH=/usr/local/share/freeling/APIs/java/
cd UIMA
export CLASSPATH="target/FreeLingWrapper-0.1-SNAPSHOT.jar":$(<classPath.txt)
java -Xmx450m -cp $CLASSPATH  edu.upf.taln.uima.freeling.FreelingXMIReaderWriter  '/input/*.txt' /output $1 xmi
#java -Xmx450m -cp $CLASSPATH  edu.upf.taln.uima.freeling.FreelingXMIReaderWriter  '/input/*.txt' /output $1 xmi
#java -Xmx450m -cp $CLASSPATH  edu.upf.taln.uima.freeling.FreelingXMIReaderWriter  'input/*.txt' output auto txt
#java -Xmx450m -cp $CLASSPATH  edu.upf.taln.uima.freeling.FreelingXMIReaderWriter  'input/nb*.txt' output nb txt
#java -Xmx450m -cp $CLASSPATH  edu.upf.taln.uima.freeling.FreelingXMIReaderWriter  'input/ru*.txt' output ru txt