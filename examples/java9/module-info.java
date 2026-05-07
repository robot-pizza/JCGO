// JCGO-SKIP: module-info isn't a normal class; the smoke-test driver
// can't translate it as a target. Slice 48 just makes sure the
// parser doesn't choke when JCGO's source scanner stumbles into a
// module-info.java in the source path — the file is parsed and the
// compilation unit ends up empty.

module com.example.foo {
    requires java.base;
    requires transitive java.logging;
    requires static java.sql;

    exports com.example.foo;
    exports com.example.foo.api to com.consumer;

    opens com.example.foo.internal;
    opens com.example.foo.internal to com.tester;

    uses com.example.foo.spi.Plugin;
    provides com.example.foo.spi.Plugin
        with com.example.foo.impl.DefaultPlugin;
}
