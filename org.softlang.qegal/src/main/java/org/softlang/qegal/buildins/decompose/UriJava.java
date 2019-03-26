package org.softlang.qegal.buildins.decompose;

import org.apache.commons.jxpath.ri.model.NodePointer;
import org.apache.commons.jxpath.ri.model.beans.BeanPointer;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;

/**
 * <pre> <b> Body: UriJava(file, xpath, result)</b></pre>
 * <pre> <b> Head: UriJava(file, sub, pred, obj) </b></pre>
 */
public class UriJava extends DecJava {

    @Override
    protected Node resolve(String filepath, NodePointer pointer) {
        if (pointer instanceof BeanPointer)
            return NodeFactory.createURI(((BeanPointer) pointer).getValue().toString());

        throw new RuntimeException("Type not matching");
    }
}
