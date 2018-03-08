FROM ubuntu:xenial
MAINTAINER Joan Codina <joan.codina@upf.edu>

ENV DEBIAN_FRONTEND noninteractive
# every RUN creates a temporal image, if an error occurs then in starts from the last successful run
# I try to get an equilibrium between many temporal images and not having to reprocess everything when there is an error

# repositories and update and upgrade to get the last versions.
RUN apt-get -qq update && \
    apt-get -qq upgrade && \
    apt-get install -qqy software-properties-common curl git build-essential --fix-missing && \
    add-apt-repository -y ppa:webupd8team/java && \
    apt-get -qq update

  
    
# Language setup
RUN apt-get -qqy install language-pack-en-base && \
    update-locale LANG=en_US.UTF-8 && \
    echo "LANGUAGE=en_US.UTF-8" >> /etc/default/locale && \
    echo "LC_ALL=en_US.UTF-8" >> /etc/default/locale && \
    locale-gen en_US.UTF-8

# Freeling Deps
RUN apt-get install -y automake autoconf libtool wget swig build-essential && \
    apt-get install -y libboost-regex-dev libicu-dev zlib1g-dev \
					   libboost-system-dev libboost-program-options-dev libboost-thread-dev maven
                       

# Install Freeling from github --- take a cofee in the meanwhile
RUN git clone --depth=1 https://github.com/TALP-UPC/FreeLing.git Freeling 
WORKDIR /Freeling
RUN autoreconf --install && ./configure && make && make install

# Java Deps

RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections && \
    apt-get install -qq -y oracle-java8-installer
# BUILD JNI
ENV JAVADIR /usr/lib/jvm/java-8-oracle
ENV SWIGDIR /usr/share/swig2.0
ENV FREELINGDIR /usr
ENV FREELINGOUT /usr/local/lib


WORKDIR /Freeling/APIs/java
RUN make all

ENV CLASSPATH=".:/usr/local/lib/"
ENV LD_LIBRARY_PATH=/usr/local/lib/
     
# Cleanup
RUN apt-get autoremove -y && apt-get clean -y && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

#UIMA component stuff
WORKDIR /
#cd RUN git clone --depth=1 https://github.com/TalnUPF/UIMA_Freeling_docker.git UIMA
RUN mkdir UIMA
WORKDIR /UIMA
#RUN mvn package
RUN mvn install
RUN export CLASSPATH="target/FreeLingWrapper-0.1-SNAPSHOT.jar":$(<classPath.txt)
RUN java -cp $CLASSPATH  edu.upf.taln.uima.freeling.FreelingXMIReaderWriter  /UIMA/input/*.txt /UIMA/output txt
#RUN java  -jar FreeLingWrapper-0.1-SNAPSHOT-jar-with-arguments.jar /UIMA/input /UIMA/output txt
#RUN mvn dependency:build-classpath -Dmdep.outputFile=classPath.txt
#RUN export CLASSPATH="target/FreeLingWrapper-0.1-SNAPSHOT.jar":$(<classPath.txt)
#RUN mvn install
#RUN  java -Xmx450m -cp $CLASSPATH  edu.upf.taln.uima.freeling.FreelingXMIReaderWriter  '/UIMA/input/*.txt' /UIMA/output en txt
#RUN  java -Xmx450m -cp $CLASSPATH  edu.upf.taln.uima.freeling.FreelingXMIReaderWriter  '/UIMA/input/*.txt' /UIMA/output auto txt
RUN java   /UIMA/input /UIMA/output txt
