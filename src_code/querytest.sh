#!/bin/bash
# querytest.sh - Run batch insert followed by a query
cd /home/evelynvb1511/510/minjava/javaminibase/src/
javac bashinsert.java query.java
make db
# Parameters for batch insertion:
H=5
L=3
DATAFILE="sampletests/sample_data_2.txt"
DBNAME="mydatabase"

# Parameters for query:
QUERYFILE="sampletests/rquery1.txt" # Use a query specification file (e.g., a range query)
INDEXOPTION="Y" # "Y" to use the index; "N" for full scan
NUMBUF=100

JAVA_DEBUG_ARGS_BATCH="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
JAVA_DEBUG_ARGS_QUERY="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5006"

echo "Running batch insertion..."
java $JAVA_DEBUG_ARGS_BATCH batchinsert $H $L $DATAFILE $DBNAME
if [ $? -ne 0 ]; then
    echo "Batch insertion failed."
    exit 1
fi

echo "Running query..."
java $JAVA_DEBUG_ARGS_QUERY query $DBNAME $QUERYFILE $INDEXOPTION $NUMBUF
if [ $? -ne 0 ]; then
    echo "Query execution failed."
    exit 1
fi

echo "Done."
