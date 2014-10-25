#!/bin/bash

usage="Usage ./test <lnx-folder>"

if [ ! -z $1 ] && [ -d $1 ]; then
	for i in "$1"*.lnx
		do
			xterm -T "$(basename $i)" -e scala -cp "bin" driver.node "$i" &
	done
else
	echo "$usage" 
fi