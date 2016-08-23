all: clean build

install:
	cp resources/scripts/migrations /usr/local/bin/migrations
	chmod a+rx /usr/local/bin/migrations
	cp target/migrations-*-standalone.jar /usr/local/bin/migrations.uber.jar
	chmod a+rx /usr/local/bin/migrations.uber.jar

uninstall:
	rm -f /usr/local/bin/migrations
	rm -f /usr/local/bin/migrations.uber.jar

build:
	lein uberjar

clean:
	lein clean
	rm -rf target/

sudoinstall:
	make clean build && sudo make install

