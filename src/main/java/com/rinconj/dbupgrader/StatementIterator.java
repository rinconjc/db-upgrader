package com.rinconj.dbupgrader;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static java.lang.Character.toChars;

/**
 * Created by julio on 3/05/15.
 */
public class StatementIterator implements Iterator<String> {
    private static final char STMT_SEPARATOR = ';';

    private final Reader reader;
    private String nextStatement;

    StatementIterator(Reader reader) {
        this.reader = reader;
    }

    private String getNextStatement() throws IOException {
        StringBuilder sb = new StringBuilder();
        int nextChar;
        while ((nextChar= reader.read())!=-1){
            boolean foundStmt = handleChar(nextChar, reader, sb);
            if(foundStmt && ! sb.toString().trim().isEmpty())
                return sb.toString().trim();

        }
        String stmt = sb.toString().trim();
        return stmt.isEmpty()?null:stmt;
    }

    private boolean handleChar(int nextChar, Reader reader, StringBuilder sb) throws IOException {
        if(nextChar == '\''){
            sb.append(toChars(nextChar));
            parseString(reader, sb);
        } else if (nextChar == '-'){
            return parseComment(reader, nextChar, sb);
        } else if (nextChar == STMT_SEPARATOR){
            return true;
        } else {
            sb.append(toChars(nextChar));
        }
        return false;
    }

    private boolean parseComment(Reader reader, int curChar,  StringBuilder sb) throws IOException {
        int nextChar = reader.read();
        if(nextChar!='-'){
            sb.append(toChars(curChar));
            return handleChar(nextChar, reader, sb);
        }
        //it is a comment, skip rest of the line!
        for(int read = reader.read(); read!=-1 && read!='\n'; read = reader.read());
        sb.append('\n');
        return false;
    }

    private void parseString(Reader reader, StringBuilder sb) throws IOException {
        int nextChar;
        int initial = sb.length();
        while ((nextChar=reader.read())!=-1){
            sb.append(toChars(nextChar));
            if(nextChar=='\''){
                return;
            }
        }
        throw new RuntimeException("Unterminated string " + sb.substring(initial));
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
