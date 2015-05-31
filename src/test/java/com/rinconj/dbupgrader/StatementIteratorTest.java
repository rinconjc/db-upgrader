package com.rinconj.dbupgrader;

import org.junit.Before;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.*;

/**
 * Created by julio on 3/05/15.
 */
public class StatementIteratorTest {

    @Test
    public void shouldHandleEmptyScript() throws Exception {
        assertFalse(new StatementIterator(new StringReader("")).hasNext());
        assertFalse(new StatementIterator(new StringReader(";\n;")).hasNext());
    }

    @Test
    public void shouldExtractSingleStatement() throws Exception {
        assertEquals("select * from dual", new StatementIterator(new StringReader("select * from dual")).next());
        assertEquals("select * from dual", new StatementIterator(new StringReader("--comment\n select * from dual;--another comment")).next());
        assertEquals("select * \nfrom dual", new StatementIterator(new StringReader("select * --comment;\nfrom dual")).next());
    }

    @Test
    public void ShouldExtractMultipleStatements() throws Exception {
        for (String s : new String[]{"statement 1\n; \nstatement 2", "statement 1\n; \nstatement 2;"}) {
            StatementIterator iterator = new StatementIterator(new StringReader(s));
            assertEquals("statement 1", iterator.next());
            assertEquals("statement 2", iterator.next());
            assertFalse(iterator.hasNext());
        }
    }

    @Test
    public void shouldHandleStrings() throws Exception {
        assertEquals("select 1 from table1 where col1='abc'", new StatementIterator(new StringReader("select 1 from table1 where col1='abc'")).next());
        assertEquals("select 1 from table1 where col1='abc\nxyz'", new StatementIterator(new StringReader("select 1 from table1 where col1='abc\nxyz'--test")).next());

        try{
            new StatementIterator(new StringReader("select 1 from table1 where col1='abc")).next();
            fail("invalid statement");
        }catch (Exception e){
            e.printStackTrace();
            //pass
        }
    }

    @Test
    public void shouldParseBlockComments() throws Exception {
        String blockComment = "/* a block comment \n second line */";
        assertEquals("select * from table1", new StatementIterator(new StringReader(blockComment + "select * from table1")).next());
        String sqlWithBlockComment = "select /* some comment or dbms metadata */ * from table1";
        assertEquals(sqlWithBlockComment, new StatementIterator(new StringReader(sqlWithBlockComment)).next());
        String sqlWithSlashAndStar = "select col1/2 /* comment * test */ from table1";
        assertEquals(sqlWithSlashAndStar, new StatementIterator(new StringReader(sqlWithSlashAndStar)).next());
    }

    @Test(expected = RuntimeException.class)
    public void shouldFailWithUnterminatedBlockComments() throws Exception {
        new StatementIterator(new StringReader("select col1, /* starting comment from test")).next();
    }
}