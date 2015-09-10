-- test batch statements

create table test_batch(col1 int, col2 varchar2(20), col3 date, col4 numeric(6,2))
;

/*
--load from file data.csv
@batch "data.csv" ({
header:true,
cols:[
{type:INTEGER}, {type:STRING},{type:DATE, format:'dd/MM/yyyy'},{type:DECIMAL, format:'#.##'}
]
})
*/

insert into test_batch(col1,col2,col3,col4) values(:$batch_0, :$batch_1, :$batch_2, :$batch_3)
;


