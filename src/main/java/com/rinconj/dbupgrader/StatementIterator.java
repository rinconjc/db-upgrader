package com.rinconj.dbupgrader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static java.lang.Character.toChars;

/**
 * Created by julio on 3/05/15.
 */
public class StatementIterator implements Iterator<String> {
    interface Parser{
        boolean parse(Reader reader, StringBuilder sb) throws IOException;
        String name();
    }

    abstract class AbstractParser implements Parser{
        final String name;
        public AbstractParser(String name){
            this.name = name;
        }

        public String name() {
            return name;
        }
    }

    private final char stmtSeparator;

    private final Parser STRING_PARSER = new AbstractParser("STRING_PARSER") {
        public boolean parse(Reader reader, StringBuilder sb) throws IOException {
            int curChar = reader.read();
            if(curChar!='\'') return false;
            int nextChar;
            int initial = sb.length();
            sb.append(toChars(curChar));
            while ((nextChar=reader.read())!=-1){
                sb.append(toChars(nextChar));
                if(nextChar=='\''){
                    return true;
                }
            }
            throw new RuntimeException("Unterminated string " + sb.substring(initial));
        }
    };

    private final Parser COMMENT_PARSER = new AbstractParser("COMMENT_PARSER") {
        public boolean parse(Reader reader, StringBuilder sb) throws IOException {
            int curChar = reader.read();
            if(curChar!='-') return false;
            int nextChar = reader.read();
            if(nextChar!='-'){
                return false;
            }
            //it is a comment, skip rest of the line!
            for(int read = reader.read(); read!=-1 && read!='\n'; read = reader.read());
            sb.append('\n');
            return true;
        }
    };

    private final Parser BLOCK_COMMENT_PARSER = new AbstractParser("BLOCK_COMMENT_PARSER") {
        public boolean parse(Reader reader, StringBuilder sb) throws IOException {
            int curChar = reader.read();
            if(curChar!='/') return false;
            int nextChar = reader.read();
            if(nextChar!='*'){
                return false;
            }
            //it is the start of block comment, extract comment
            StringBuilder comment = new StringBuilder();
            while (true){
                int read;
                for(read = reader.read(); read!=-1 && read!='*'; read = reader.read())
                    comment.append(toChars(read));
                if(read==-1)
                    throw new RuntimeException("Unterminated block comment, missing */ after: " + comment.toString());
                comment.append(toChars(read));
                read = reader.read();
                if(read==-1)
                    throw new RuntimeException("Unterminated block comment, missing */ after: " + comment.toString());
                comment.append(toChars(read));
                if(read=='/') break;
            }
            if(sb.length()>0) //include block comment if not at the beginning of sentence.
                sb.append("/*").append(comment);
            return true;
        }
    };

    private final Parser END_OF_STATEMENT_PARSER = new AbstractParser("END_OF_STATEMENT_PARSER") {
        public boolean parse(Reader reader, StringBuilder sb) throws IOException {
            int curChar = reader.read();
            return curChar==stmtSeparator || curChar == -1;
        }
    };

    private final Parser DEFAULT_PARSER = new AbstractParser("DEFAULT_PARSER") {
        public boolean parse(Reader reader, StringBuilder sb) throws IOException {
            int curChar = reader.read();
            if(curChar==-1) return false;
            sb.append(toChars(curChar));
            return true;
        }
    };

    private final Parser END_OF_SCRIPT = new AbstractParser("END_OF_SCRIPT") {
        public boolean parse(Reader reader, StringBuilder sb) throws IOException {
            return reader.read()==-1;
        }
    };

    private final Parser[] parsers = new Parser[]{STRING_PARSER, COMMENT_PARSER, BLOCK_COMMENT_PARSER, END_OF_STATEMENT_PARSER, DEFAULT_PARSER, END_OF_SCRIPT};

    private final Reader reader;
    private String nextStatement;

    StatementIterator(Reader reader) {
        this(reader, ';');
    }

    StatementIterator(Reader reader, char stmtSeparator) {
        this.reader = reader.markSupported()? reader: new BufferedReader(reader);
        this.stmtSeparator = stmtSeparator;
    }

    private String getNextStatement() throws IOException {
        StringBuilder sb = new StringBuilder();
        Parser p;
        do{
            p = parseNext(reader, sb);
        }while (p!=END_OF_STATEMENT_PARSER && p!=END_OF_SCRIPT);
        String stmt = sb.toString().trim();
        return stmt.isEmpty()?null:stmt;
    }

    Parser parseNext(Reader reader, StringBuilder sb) throws IOException {
        reader.mark(10);
        for (Parser parser : parsers) {
            if(parser.parse(reader, sb)) return parser;
            reader.reset();
        }
        //nothing matched?
        throw new RuntimeException("no parsers matched!, current content:" + sb.toString());
    }

    public boolean hasNext() {
        if(nextStatement!=null) return true;
        try {
            nextStatement = getNextStatement();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return nextStatement!=null;
    }

    public String next() {
        if(hasNext()){
            String temp = nextStatement;
            nextStatement = null;
            return temp;
        }
        throw new NoSuchElementException();
    }

    public void remove() {
        throw new UnsupportedOperationException("not supported");
    }
}



