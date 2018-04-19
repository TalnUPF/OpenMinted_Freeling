#!/bin/bash
#language default value
LANG="auto" 
POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"

case $key in
    --input)
    INPUT="'$2/*.xmi'"
    shift # past argument
    shift # past value
    ;;
    --output)
    OUTPUT="$2"
    shift # past argument
    shift # past value
    ;;
    --param:language=*)
    LANG="${key#*=}"
    shift # past argument
    shift # past value
    ;;
    *)    # unknown option
    POSITIONAL+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters

echo INPUT  = "${INPUT}"
echo OUTPUT  = "${OUTPUT}"
echo LANG    = "${LANG}"

export LD_LIBRARY_PATH=/usr/local/share/freeling/APIs/java/
cd UIMA
export CLASSPATH="target/FreeLingWrapper-0.1-SNAPSHOT.jar":$(<classPath.txt)
java -Xmx450m -cp $CLASSPATH  edu.upf.taln.uima.freeling.FreelingXMIReaderWriter ${INPUT} ${OUTPUT}  ${LANG} xmi
