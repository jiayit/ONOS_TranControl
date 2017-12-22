package org.onosproject.test.rest;

import org.onlab.rest.AbstractWebApplication;

import java.util.Set;

/**
 * Created by jiayit on 6/7/17.
 */
public class TestWebApplication extends AbstractWebApplication {

    @Override
    public Set<Class<?>> getClasses() {
        return getClasses(TestWebResource.class);
    }
}
