create table course (
    id varchar(40) primary key,
    code varchar(20) unique not null,
    title varchar(180) not null,
    department varchar(80) not null,
    units int not null check (units > 0),
    schedule varchar(150) not null,
    room varchar(80) not null,
    faculty varchar(150) not null,
    capacity int not null check (capacity >= 0),
    open boolean not null default true
);
create table enrollment (
    student_id varchar(40) not null,
    course_id varchar(40) not null references course(id),
    enrolled_at timestamptz not null default now(),
    primary key(student_id, course_id)
);
insert into course values
 ('CS301', 'CS 301', 'Distributed Systems', 'Computer Science', 3, 'Mon / Wed · 09:00–10:30', 'Innovation Hall 302', 'Dr. Jordan Reyes', 30, true),
 ('CS302', 'CS 302', 'Software Engineering', 'Computer Science', 3, 'Tue / Thu · 10:30–12:00', 'Innovation Hall 204', 'Prof. Maya Chen', 35, true),
 ('CS303', 'CS 303', 'Database Systems', 'Computer Science', 3, 'Mon / Wed · 13:00–14:30', 'Technology Center 101', 'Dr. Luis Santos', 28, true),
 ('MATH301', 'MATH 301', 'Applied Statistics', 'Mathematics', 3, 'Tue / Thu · 14:30–16:00', 'Science Building 206', 'Prof. Elena Cruz', 40, true),
 ('GE301', 'GE 301', 'Ethics in Technology', 'General Education', 3, 'Fri · 09:00–12:00', 'Liberal Arts 105', 'Prof. Adrian Lim', 45, true),
 ('CS304', 'CS 304', 'Human–Computer Interaction', 'Computer Science', 3, 'Fri · 13:00–16:00', 'Design Studio 2', 'Prof. Maya Chen', 25, false);
