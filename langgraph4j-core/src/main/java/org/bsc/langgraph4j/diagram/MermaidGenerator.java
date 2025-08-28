package org.bsc.langgraph4j.diagram;

import org.bsc.langgraph4j.DiagramGenerator;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * This class represents a MermaidGenerator that extends DiagramGenerator. It generates a flowchart using Mermaid syntax.
 * The flowchart includes various nodes such as start, stop, web_search, retrieve, grade_documents, generate, transform_query,
 * and different conditional states.
 */
public class MermaidGenerator extends DiagramGenerator {

    //public static final char SUBGRAPH_PREFIX = '_';

    private String formatNode( String id, Context ctx ) {
        if( !ctx.isSubGraph() ) {
            return id;
        }

        if( ctx.anySubGraphWithId(id) ) {
            return id;
        }

        if( isStart(id) || isEnd(id)) {
            return format( "%s%s", id, ctx.title());
        }

        return format("%s_%s", id, ctx.title());
    }

    @Override
    protected void appendHeader( Context ctx ) {
        if( ctx.isSubGraph() ) {
                ctx.sb()
                .append(format("subgraph %s\n", ctx.title()))
                .append(format("\t%1$s((start)):::%1$s\n", formatNode(START, ctx)))
                .append(format("\t%1$s((stop)):::%1$s\n", formatNode(END, ctx)))
                ;
        }
        else {
                ofNullable(ctx.title())
                    .map( title -> ctx.sb().append(format("---\ntitle: %s\n---\n", title)) )
                    .orElseGet(ctx::sb)
                .append("flowchart TD\n")
                .append(format("\t%s((start))\n", START))
                .append(format("\t%s((stop))\n", END))
                ;
        }
    }

    @Override
    protected void appendFooter(Context ctx) {
        if( ctx.isSubGraph() ) {
            ctx.sb().append("end\n");
        }
        else {
            ctx.sb()
                .append('\n')
                .append( format("\tclassDef %s fill:black,stroke-width:1px,font-size:xx-small;\n", formatNode(START, ctx)))
                .append( format("\tclassDef %s fill:black,stroke-width:1px,font-size:xx-small;\n", formatNode(END, ctx) ));
        }
    }

   @Override
   protected void declareConditionalStart(Context ctx, String name) {
       ctx.sb().append('\t');
       ctx.sb().append( format("%s{\"check state\"}\n", formatNode(name, ctx) ) );
   }

   @Override
   protected void declareNode(Context ctx, String name) {
       ctx.sb().append('\t');
       ctx.sb().append( format( "%s(\"%s\")\n", formatNode(name, ctx), name ) );
   }

   @Override
   protected void declareConditionalEdge(Context ctx, int ordinal) {
       ctx.sb().append('\t');
       ctx.sb().append( format("%s{\"check state\"}\n", formatNode( format("condition%d", ordinal), ctx )));
   }

    @Override
    protected void commentLine(Context ctx, boolean yesOrNo) {
        if (yesOrNo) ctx.sb().append( "\t%%" );
    }

    @Override
    protected void call(Context ctx, String from, String to, CallStyle style) {
        ctx.sb().append('\t');

        from = formatNode( from, ctx);
        to = formatNode( to, ctx );
        ctx.sb().append(
                switch( style ) {
                    case CONDITIONAL -> format("%1$s:::%1$s -.-> %2$s:::%2$s\n", from, to);
                    default ->  format("%1$s:::%1$s --> %2$s:::%2$s\n", from, to);
                });
    }

    @Override
    protected void call(Context ctx, String from, String to, String description, CallStyle style) {
        ctx.sb().append('\t');
        from = formatNode( from, ctx);
        to = formatNode( to, ctx );

        ctx.sb().append(
                switch( style ) {
                    case CONDITIONAL -> format("%1$s:::%1$s -.->|%2$s| %3$s:::%3$s\n", from, description, to);
                    default ->  format("%1$s:::%1s -->|%2$s| %3$s:::%3$s\n", from, description, to);
                });
    }
}
