CLEANABLE_FILES=rv-predict.jar.copy
FILES=$(CLEANABLE_FILES)

FILESNAME_rv-predict.jar.copy=rv-predict.jar

MVN?=mvn

FILESMODE_rv-predict.jar.copy=0644

FILESDIR_rv-predict.jar.copy=$(DATADIR)/rv-predict-c

MAVEN_VERSION!=cd $(.CURDIR)/.. && $(MVN) -q -Dexec.executable="echo" -Dexec.args='$${project.version}' --non-recursive org.codehaus.mojo:exec-maven-plugin:1.3.1:exec

.if ${NOISY:Uno} == "no"
MAVEN_OPTS=-q
.else
MAVEN_OPTS=
.endif

.if ${DEBUG:Uno} == "no"
MAVEN_OPTS+=-Dobfuscate
.endif

buildit: .PHONY
.if $(PREBUILT:Uno) == "yes"
	@echo rv-predict.jar is prebuilt.
.else
	cd $(.CURDIR)/.. && $(MVN) -q -DonlyJar -Dskip_installer_test -DskipDocs -DskipTests -U $(MAVEN_OPTS) package
.endif

rv-predict.jar.copy: $(.CURDIR)/../jar/target/jar-${MAVEN_VERSION}.jar
	@cp $(.ALLSRC:Ncopyit) $(.OBJDIR)/$(.TARGET)
	@chmod 0644 $(.OBJDIR)/$(.TARGET)
.if $(PREBUILT:Uno) == "yes"
	@echo Installed prebuilt rv-predict.jar.
.endif

$(.CURDIR)/../jar/target/jar-${MAVEN_VERSION}.jar: buildit

clean cleandir distclean:
	@cd $(.CURDIR)/.. && $(MVN) -q clean

#CLEANDIRS+=$(.CURDIR)/../target $(.CURDIR)/../java/target
CLEANFILES+=$(CLEANABLE_FILES)

.include <mkc.files.mk>
