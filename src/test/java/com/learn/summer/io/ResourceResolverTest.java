package com.learn.summer.io;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.annotation.sub.AnnoScan;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceResolverTest {

    @Test
    public void scanClass() {
        var pkg = "com.learn.scan";
        var rr = new ResourceResolver(pkg);
        List<String> classes = rr.scan(res -> {
            String name = res.name();
            if (name.endsWith(".class")) {
                return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
            }
            return null;
        });
        Collections.sort(classes);
        System.out.println(classes);
        String[] listClasses = new String[] {
                // list of some scan classes:
                "com.learn.scan.convert.ValueConverterBean", //
                "com.learn.scan.destroy.AnnotationDestroyBean", //
                "com.learn.scan.init.SpecifyInitConfigBean", //
                "com.learn.scan.proxy.OriginBean", //
                "com.learn.scan.proxy.FirstProxyBeanProcessor", //
                "com.learn.scan.proxy.SecondProxyBeanProcessor", //
                "com.learn.scan.nested.OuterBean", //
                "com.learn.scan.nested.OuterBean$InnerBean", //
                "com.learn.scan.sub1.Sub1Bean", //
                "com.learn.scan.sub1.sub2.Sub2Bean", //
                "com.learn.scan.sub1.sub2.sub3.Sub3Bean", //
        };
        for (String clazz : listClasses) {
            assertTrue(classes.contains(clazz));
        }
    }

    @Test
    public void scanJar() {
        var pkg = PostConstruct.class.getPackageName();
        var rr = new ResourceResolver(pkg);
        List<String> classes = rr.scan(res -> {
            String name = res.name();
            if (name.endsWith(".class")) {
                return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
            }
            return null;
        });
        // classes in jar:
        assertTrue(classes.contains(PostConstruct.class.getName()));
        assertTrue(classes.contains(PreDestroy.class.getName()));
        assertTrue(classes.contains(PermitAll.class.getName()));
        assertTrue(classes.contains(DataSourceDefinition.class.getName()));
        // jakarta.annotation.sub.AnnoScan is defined in classes:
        assertTrue(classes.contains(AnnoScan.class.getName()));
    }

    @Test
    public void scanTxt() {
        var pkg = "com.learn.scan";
        var rr = new ResourceResolver(pkg);
        List<String> classes = rr.scan(res -> {
            String name = res.name();
            System.out.println(name);
            if (name.endsWith(".txt")) {
                return name.replace("\\", "/");
            }
            return null;
        });
        Collections.sort(classes);
        assertArrayEquals(new String[] {
                // txt files:
                "com/learn/scan/sub1/sub1.txt", //
                "com/learn/scan/sub1/sub2/sub2.txt", //
                "com/learn/scan/sub1/sub2/sub3/sub3.txt", //
        }, classes.toArray(String[]::new));
    }
}
