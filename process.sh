#!/bin/bash

cd UIMA
export CLASSPATH="target/FreeLingWrapper-0.1-SNAPSHOT.jar":$(<classPath.txt)
java -Xmx450m -cp $CLASSPATH  edu.upf.taln.uima.freeling.FreelingXMIReaderWriter  '/input/*.txt' /output $1 xmi