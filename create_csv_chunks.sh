#!/bin/bash

# Create three 10k row compressed CSV files, from the 5M row archive (the .bz2 file)

bzcat ~/Data/Chicago_Crimes_2001_to_04.19.2015.csv.bz2 | sed -n 2,10001p | gzip - > chicago_crimes_10k_part1.csv.gz
bzcat ~/Data/Chicago_Crimes_2001_to_04.19.2015.csv.bz2 | sed -n 10002,20001p | gzip - > chicago_crimes_10k_part2.csv.gz
bzcat ~/Data/Chicago_Crimes_2001_to_04.19.2015.csv.bz2 | sed -n 20002,30001p | gzip - > chicago_crimes_10k_part3.csv.gz

