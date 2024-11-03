package com.learn.scan;

import com.learn.imported.LocalDateConfiguration;
import com.learn.imported.ZonedDateConfiguration;
import com.learn.summer.annotation.ComponentScan;
import com.learn.summer.annotation.Import;

@ComponentScan
@Import({LocalDateConfiguration.class, ZonedDateConfiguration.class})
public class ScanApplication {
}
