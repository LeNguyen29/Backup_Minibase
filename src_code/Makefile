#This Makefile is used to create assignments.

#the following macros should be updated according to
#the assignment to be generated

JARFILES=bufmgr/*.class diskmgr/*.class global/*.class iterator/*.class\
         heap/*.class chainexception/*.class  btree/*.class index/*.class tests/*.class batchinsert.class query.class

JDKPATH = /usr/lib/jvm/java-21-openjdk-amd64
LIBPATH = .:..
CLASSPATH = $(LIBPATH)
BINPATH = $(JDKPATH)/bin
JAVAC = $(JDKPATH)/bin/javac -classpath $(CLASSPATH)
JAVA  = $(JDKPATH)/bin/java  -classpath $(CLASSPATH)

DOCFILES=bufmgr diskmgr global chainexception heap btree iterator index

##############  update the above for each assignment in making

ASSIGN=/home/candan/Courses/CSE510spr02/minjava/javaminibase/src
LIBDIR=$(ASSIGN)/lib
KEY=$(ASSIGN)/key
SRC=$(ASSIGN)/src

IMAGELINK=$(PACKAGEINDEX)/images
PACKAGEINDEX=$(ASSIGN)/javadoc

JAVADOC=javadoc -public -d $(PACKAGEINDEX)

### Generate jar and javadoc files.  Apply to most assignments.
db: 
	make -C global
	make -C chainexception
	make -C btree
	make -C bufmgr
	make -C diskmgr
	make -C heap
	make -C index
	make -C iterator
	$(JAVAC) batchinsert.java
	$(JAVAC) query.java
	
doc:
	$(JAVADOC) $(DOCFILES)

test: 
	cd tests; make bmtest dbtest; whoami; make hftest bttest indextest jointest sorttest sortmerge

clean:
	\rm -f $(CLASSPATH)/*.class *~ \#* core $(JARFILES) TRACE

