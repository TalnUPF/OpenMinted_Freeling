#!/bin/bash
#this bash is to store the correct command to run the docker (from outside the docker ;-) 
#docker pull taln/openminted_freeling
docker run -v /var/data/openminted/input:/input -v /var/data/openminted/output:output openminted_freeling 