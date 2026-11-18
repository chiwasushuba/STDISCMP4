create table teaching_class (
    course_code varchar(20) not null,
    term varchar(40) not null,
    course_title varchar(180) not null,
    faculty_id varchar(40) not null,
    units int not null check (units > 0),
    primary key(course_code, term)
);
create table grade_record (
    student_id varchar(40) not null,
    student_name varchar(150) not null,
    course_code varchar(20) not null,
    term varchar(40) not null,
    value varchar(8) not null default '',
    primary key(student_id, course_code, term),
    foreign key(course_code, term) references teaching_class(course_code, term)
);
insert into teaching_class values
 ('CS 201', '2025–2026 · Semester 2', 'Data Structures & Algorithms', 'FAC-001', 3),
 ('CS 202', '2025–2026 · Semester 2', 'Object-Oriented Programming', 'FAC-001', 3),
 ('MATH 201', '2025–2026 · Semester 2', 'Discrete Mathematics', 'FAC-002', 3),
 ('CS 301', '2026–2027 · Semester 1', 'Distributed Systems', 'FAC-001', 3);
insert into grade_record values
 ('2024-001', 'Alex Morgan', 'CS 201', '2025–2026 · Semester 2', '1.50'),
 ('2024-001', 'Alex Morgan', 'CS 202', '2025–2026 · Semester 2', '1.25'),
 ('2024-001', 'Alex Morgan', 'MATH 201', '2025–2026 · Semester 2', '1.75'),
 ('2024-002', 'Sam Rivera', 'CS 201', '2025–2026 · Semester 2', '1.75'),
 ('2024-002', 'Sam Rivera', 'CS 202', '2025–2026 · Semester 2', '1.50'),
 ('2024-001', 'Alex Morgan', 'CS 301', '2026–2027 · Semester 1', ''),
 ('2024-002', 'Sam Rivera', 'CS 301', '2026–2027 · Semester 1', '');
