package com.learn.summer.context;

import com.learn.imported.LocalDateConfiguration;
import com.learn.imported.ZonedDateConfiguration;
import com.learn.scan.ScanApplication;
import com.learn.scan.convert.ValueConverterBean;
import com.learn.scan.custom.annotation.CustomAnnotationBean;
import com.learn.scan.destroy.AnnotationDestroyBean;
import com.learn.scan.destroy.SpecifyDestroyBean;
import com.learn.scan.init.AnnotaionInitBean;
import com.learn.scan.init.SpecifyInitBean;
import com.learn.scan.nested.OuterBean;
import com.learn.scan.primary.DogBean;
import com.learn.scan.primary.PersonBean;
import com.learn.scan.primary.StudentBean;
import com.learn.scan.proxy.InjectProxyOnConstructorBean;
import com.learn.scan.proxy.InjectProxyOnPropertyBean;
import com.learn.scan.proxy.OriginBean;
import com.learn.scan.proxy.SecondProxyBean;
import com.learn.scan.sub1.sub2.sub3.Sub3Bean;
import com.learn.summer.io.PropertyResolver;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class ContextTest {
    @Test
    public void testCustomAnnotation() {
        try (var ctx = new AnnotationConfigApplicationContext(
                ScanApplication.class, createPropertyResolver())) {
            assertNotNull(ctx.getBean(CustomAnnotationBean.class));
            assertNotNull(ctx.getBean("customAnnotation"));
        }
    }

    @Test
    public void testInitMethod() {
        try (var ctx = new AnnotationConfigApplicationContext(
                ScanApplication.class, createPropertyResolver())) {
            var bean1 = ctx.getBean(AnnotaionInitBean.class);
            var bean2 = ctx.getBean(SpecifyInitBean.class);
            assertEquals("Scan App / v1.0", bean1.appName);
            assertEquals("Scan App / v1.0", bean2.appName);
        }
    }

    @Test
    public void testImport() {
        try (var ctx = new AnnotationConfigApplicationContext(
                ScanApplication.class, createPropertyResolver())) {
            assertNotNull(ctx.getBean(LocalDateConfiguration.class));
            assertNotNull(ctx.getBean(ZonedDateConfiguration.class));
            assertNotNull(ctx.getBean("startLocalDate"));
            assertNotNull(ctx.getBean("startLocalDateTime"));
            assertNotNull(ctx.getBean("startZonedDateTime"));
        }
    }

    @Test
    public void testDestroyMethod() {
        AnnotationDestroyBean bean1 = null;
        SpecifyDestroyBean bean2 = null;
        try(var ctx = new AnnotationConfigApplicationContext(
                ScanApplication.class, createPropertyResolver())) {
            bean1 = ctx.getBean(AnnotationDestroyBean.class);
            bean2 = ctx.getBean(SpecifyDestroyBean.class);
            assertEquals("Scan App", bean1.appTitle);
            assertEquals("Scan App", bean2.appTitle);
        }
        assertNull(bean1.appTitle);
        assertNull(bean2.appTitle);
    }

    @Test
    public void testConvertor() {
        try(var ctx = new AnnotationConfigApplicationContext(
                ScanApplication.class, createPropertyResolver())) {
            var bean = ctx.getBean(ValueConverterBean.class);

            assertNotNull(bean.injectedBoolean);
            assertTrue(bean.injectedBoolean);
            assertTrue(bean.injectedBooleanPrimitive);

            assertNotNull(bean.injectedByte);
            assertEquals((byte) 123, bean.injectedByte);
            assertEquals((byte) 123, bean.injectedBytePrimitive);

            assertNotNull(bean.injectedShort);
            assertEquals((short) 12345, bean.injectedShort);
            assertEquals((short) 12345, bean.injectedShortPrimitive);

            assertNotNull(bean.injectedInteger);
            assertEquals(1234567, bean.injectedInteger);
            assertEquals(1234567, bean.injectedIntPrimitive);

            assertNotNull(bean.injectedLong);
            assertEquals(123456789_000L, bean.injectedLong);
            assertEquals(123456789_000L, bean.injectedLongPrimitive);

            assertNotNull(bean.injectedFloat);
            assertEquals(12345.6789F, bean.injectedFloat, 0.0001F);
            assertEquals(12345.6789F, bean.injectedFloatPrimitive, 0.0001F);

            assertNotNull(bean.injectedDouble);
            assertEquals(123456789.87654321, bean.injectedDouble, 0.0000001);
            assertEquals(123456789.87654321, bean.injectedDoublePrimitive, 0.0000001);

            assertEquals(LocalDate.parse("2023-03-29"), bean.injectedLocalDate);
            assertEquals(LocalTime.parse("20:45:01"), bean.injectedLocalTime);
            assertEquals(LocalDateTime.parse("2023-03-29T20:45:01"), bean.injectedLocalDateTime);
            assertEquals(ZonedDateTime.parse("2023-03-29T20:45:01+08:00[Asia/Shanghai]"), bean.injectedZonedDateTime);
            assertEquals(Duration.parse("P2DT3H4M"), bean.injectedDuration);
            assertEquals(ZoneId.of("Asia/Shanghai"), bean.injectedZoneId);
        }
    }

    @Test
    public void testNested() {
        try(var ctx = new AnnotationConfigApplicationContext(
                ScanApplication.class, createPropertyResolver())) {
            assertNotNull(ctx.getBean(OuterBean.InnerBean.class));
        }
    }

    @Test
    public void testPrimary() {
        try(var ctx = new AnnotationConfigApplicationContext(
                ScanApplication.class, createPropertyResolver())) {
            var person = ctx.getBean(PersonBean.class);
            assertEquals(StudentBean.class, person.getClass());
            var dog = ctx.getBean(DogBean.class);
            assertEquals("husky", dog.type);
        }
    }

    @Test
    public void testProxy() {
        try(var ctx = new AnnotationConfigApplicationContext(
                ScanApplication.class, createPropertyResolver())) {
            OriginBean proxy = ctx.getBean(OriginBean.class);
            assertSame(SecondProxyBean.class, proxy.getClass());
            assertEquals("Scan App", proxy.getName());
            assertEquals("v1.0", proxy.getVersion());
            // 确保 proxy 中字段没被注入
            assertNull(proxy.name);
            assertNull(proxy.version);

            var inject1 = ctx.getBean(InjectProxyOnPropertyBean.class);
            var inject2 = ctx.getBean(InjectProxyOnConstructorBean.class);
            assertSame(proxy, inject1.injected);
            assertSame(proxy, inject2.injected);
        }
    }

    @Test
    public void testSub() {
        try(var ctx = new AnnotationConfigApplicationContext(
                ScanApplication.class, createPropertyResolver())) {
            var sub3Bean = ctx.getBean(Sub3Bean.class);
            assertSame(Sub3Bean.class, sub3Bean.getClass());
        }
    }

    PropertyResolver createPropertyResolver() {
        var ps = new Properties();
        ps.put("app.title", "Scan App");
        ps.put("app.version", "v1.0");
        ps.put("jdbc.url", "jdbc:hsqldb:file:testdb.tmp");
        ps.put("jdbc.username", "sa");
        ps.put("jdbc.password", "");
        ps.put("convert.boolean", "true");
        ps.put("convert.byte", "123");
        ps.put("convert.short", "12345");
        ps.put("convert.integer", "1234567");
        ps.put("convert.long", "123456789000");
        ps.put("convert.float", "12345.6789");
        ps.put("convert.double", "123456789.87654321");
        ps.put("convert.localdate", "2023-03-29");
        ps.put("convert.localtime", "20:45:01");
        ps.put("convert.localdatetime", "2023-03-29T20:45:01");
        ps.put("convert.zoneddatetime", "2023-03-29T20:45:01+08:00[Asia/Shanghai]");
        ps.put("convert.duration", "P2DT3H4M");
        ps.put("convert.zoneid", "Asia/Shanghai");
        var pr = new PropertyResolver(ps);
        return pr;
    }
}
