package uncake

class GradesController {

    final SUBJECT_CREDITS = 6, SUBJECT_GRADE = 9, SUBJECT_NAME = 1, SUBJECT_CODE = 0, SUBJECT_TYPOLOGY = 5
    final ADVANCE_FUND = 1, ADVANCE_DISC = 2, ADVANCE_FREE = 3, ADVANCE_NIV = 5

    def planPattern = "[0-9]+ \\| [A-Za-z:\\.\\- ]+\n"
    def subjectPattern = "[0-9][A-Z\\-0-9]*[\\t][A-Za-z:\\(\\)\\.\\- ]+[\\t][0-9]+[\\t][0-9]+[\\t][0-9]+[\\t][A-Z][\\t][0-9]+[\\t][0-9]+[\\t][A-Za-z]?[\\t][0-9]\\.?[0-9]"
    def subjectLevelPattern = "[0-9][A-Z\\-0-9]*[\\t][A-Za-z:\\(\\)\\.\\- ]+[\\t][0-9]+[\\t][0-9]+[\\t][0-9]+[\\t][A-Z][\\t][0-9]+[\\t][0-9]+[\\t][A-Za-z][\\t]AP"
    def requiredPattern = "exigidos\\t[0-9]+\\t[0-9]+\\t[0-9]+\\t[0-9]+\\t[0-9\\-]+\\t[0-9]+"
    def approvedPattern = "aprobados\\t[0-9]+\\t[0-9]+\\t[0-9]+\\t[0-9]+\\t[0-9\\-]+\\t[0-9]+"
    def periodPattern = "[0-9]+[\\t]periodo academico[ ]*\\|[ ]*[0-9\\-A-Z]+"

    def index() { }

    /*
    *   Receive Academic record from the text area
    *   Return to javascript data which is split by '&&&'
    *   1. PAPA
    *   2. PA
    *   3. Period names
    *   4. subjects
    *   5. Advance components
    *   6. Advance
    *   7. Plan
    */

    def calculateAcademicRecord(){
        def academicRecord = String.valueOf( params.academicRecord )
        def valid = academicRecord.find( planPattern ) != null & academicRecord.find( subjectPattern ) != null & academicRecord.find( periodPattern ) != null
        valid &= academicRecord.find( requiredPattern ) != null & academicRecord.find( approvedPattern ) != null
        if( !valid )
            render false
        else {
            def codeStudyPlan = Integer.parseInt(String.valueOf(academicRecord.find(planPattern)).split('\\|')[0].trim())
            def nameStudyPlan = String.valueOf(academicRecord.find(planPattern)).trim()
            def studyPlan = uncake.StudyPlan.findByCode(codeStudyPlan)
            studyPlan.fundamentalCredits = studyPlan.fundamentalCredits == null ? Integer.parseInt(academicRecord.find(requiredPattern).split('\\t')[ADVANCE_FUND]) : studyPlan.fundamentalCredits
            studyPlan.disciplinaryCredits = studyPlan.disciplinaryCredits == null ? Integer.parseInt(academicRecord.find(requiredPattern).split('\\t')[ADVANCE_DISC]) : studyPlan.disciplinaryCredits
            studyPlan.freeChoiceCredits = studyPlan.freeChoiceCredits == null ? Integer.parseInt(academicRecord.find(requiredPattern).split('\\t')[ADVANCE_FREE]) : studyPlan.freeChoiceCredits
            studyPlan.languageCredits = studyPlan.languageCredits == null ? Integer.parseInt(academicRecord.find(requiredPattern).split('\\t')[ADVANCE_NIV]) : studyPlan.languageCredits

            def periods = getPeriods(academicRecord)
            def advanceComponents = getAdvanceComponents(academicRecord)

            def data = getPAPA(periods) + "&&&" + getPA(periods) + "&&&" + getPeriodNames(academicRecord) + "&&&"
            data += getSubjects(periods) + "&&&" + advanceComponents + "&&&" + getAdvance(advanceComponents) + "&&&" + nameStudyPlan
            render data
        }
    }

    def calculateProjection(){
        def input = params.inputData
        def projectedCredits = input.split("&&&")[0].split("&&")
        def projectedGrades = input.split("&&&")[1].split("&&")
        def projectedAverage = Double.parseDouble( input.split("&&&")[2] ) - 0.05
        def subjects = input.split("&&&")[3].replace("'", "").split(",")
        def gradedCredits = []
        def ungradedCredits = []
        def grades = []
        def sumGrades = 0.0
        def sumCredits = 0.0
        def sumUngradedCredits = 0.0
        projectedCredits.eachWithIndex{ it, i ->
            if( Double.parseDouble( projectedGrades[i] ) != -1 ){
                gradedCredits.add( Integer.valueOf(it) )
                grades.add( Double.parseDouble( projectedGrades[i] ) )
            }else
                ungradedCredits.add( Integer.valueOf(it) )
        }
        subjects.each{
            def subject = String.valueOf( it ).replace('\\t', '&&')
            def credits = Integer.parseInt( subject.split('&&')[SUBJECT_CREDITS] )
            def grade = Double.parseDouble( subject.split('&&')[SUBJECT_GRADE] )
            sumGrades += grade * credits
            sumCredits += credits
        }
        gradedCredits.eachWithIndex{ it, i ->
            sumGrades += grades[i] * it
            sumCredits += it
        }
        if( ungradedCredits.size() > 0 ){
            ungradedCredits.each {
                sumUngradedCredits += it
            }
            def requiredGrade = ( projectedAverage * ( sumCredits + sumUngradedCredits ) - sumGrades ) / sumUngradedCredits
            render 0 + "&&&" + requiredGrade + "&&&" + ungradedCredits.size()
        }else {
            def averageObtained = sumCredits > 0 ? sumGrades / sumCredits : 0.0
            render 1 + "&&&" + averageObtained + "&&&" + gradedCredits.size()
        }
    }

    def existAcademicRecord(){
        def planPattern = "[0-9]+ \\| [A-Za-z:\\.\\- ]+"
        def record = String.valueOf( params.record )
        def codeStudyPlan = Integer.parseInt( String.valueOf( record.find(planPattern) ).split('\\|')[0].trim() )
        def studyPlan = uncake.StudyPlan.findByCode( codeStudyPlan )
        def studyPlanCreated = false;
        def newUser = uncake.User.findById( ((User)session.user).id )
        newUser.academicRecord.each{
            if( it.studyPlan.code == studyPlan.code )
                studyPlanCreated = true
        }
        if( studyPlanCreated )
            render '1'
        else
            render '0'
    }

    def saveAcademicRecord(){
        def academicRecord = String.valueOf( params.record )
        def periods = getPeriods( academicRecord )
        def periodNames = getPeriodNames( academicRecord )
        def newUser = uncake.User.findById( ( (User)session.user ).id )
        def codeStudyPlan = Integer.parseInt( String.valueOf( academicRecord.find(planPattern) ).split('\\|')[0].trim() )
        def studyPlan = uncake.StudyPlan.findByCode( codeStudyPlan )
        def studyPlanCreated = false
        def coursesToSave = getCoursesToSave( periods, periodNames )
        coursesToSave.each{ it.save(failOnError: true) }

        newUser.academicRecord.each{
            if( it.studyPlan.code == studyPlan.code )
                studyPlanCreated = true
        }
        if( studyPlanCreated ){
            def delStudyPlan = []
            if( delStudyPlan != null ) {
                newUser.academicRecord.each{
                    if( uncake.AcademicRecord.findById( it.id ).studyPlan.code == studyPlan.code )
                        delStudyPlan.add(it)
                }
            }
            delStudyPlan.each {
                newUser.removeFromAcademicRecord( AcademicRecord.findById( ( (AcademicRecord)it ).id ) )
            }
        }
        def academicRecordToSave = new uncake.AcademicRecord( studyPlan: studyPlan, credits: getSumCredits(periods), PAPA: getPAPA(periods), PA: getPA(periods), courses: coursesToSave )
        newUser.addToAcademicRecord( academicRecordToSave ).save(failOnError: true)
        render ""
    }

    def loadAcademicRecord(){
        def selectedRecord = String.valueOf( params.record )
        def selectedCode = Integer.parseInt( selectedRecord.split('\\|')[0].trim() )
        def selectedName = selectedRecord.split('\\|')[1].trim()
        def academicRecordToShow
        def periods = []
        def periodsToSort = []
        def subjectsPAPA = []
        def gradesPAPAPerPeriod = []
        def creditsPAPAPerPeriod = []
        def PAPAPerPeriod = []
        def gradesPAPerPeriod = []
        def creditsPAPerPeriod = []
        def PAPerPeriod = []
        def gradesPAPASoFar = 0.0
        def creditsPAPASoFar = 0
        def gradesPASoFar = 0.0
        def creditsPASoFar = 0
        def creditsFundamentals = 0
        def creditsDisciplinary = 0
        def creditsFreeChoice = 0
        def creditsLanguage = 0
        def subjectsToPrint = []
        def advanceComponents = []

        uncake.User.findById( ((User)session.user).id ).academicRecord.each {
            if( it.studyPlan.code == selectedCode && it.studyPlan.name.toUpperCase().equals(selectedName) )
                academicRecordToShow = (AcademicRecord)it
        }

        academicRecordToShow.courses.each{
            def periodNumber = ((uncake.Course)it).semesterNumber
            def periodName = ((uncake.Course)it).semester
            def typology = ((uncake.Course)it).typology
            if( !typology.equals('Idioma y nivelación') )
                subjectsPAPA.add( ((uncake.Course)it) )
            else
                creditsLanguage += ((uncake.Course)it).credits
            if( !(periodNumber + "&&&" + periodName in periodsToSort) ) {
                periodsToSort.add(periodNumber + "&&&" + periodName)
                periods.add('')
            }
        }
        periodsToSort.each{
            periods[ Integer.parseInt( it.split("&&&")[0] ) - 1 ] = it.split("&&&")[1]
        }
        def subjectsPA = subjectsPAPA
        subjectsPAPA.eachWithIndex{ subject, i ->
            subjectsPAPA.eachWithIndex{ subject2, j ->
                if( i != j ){
                    if( ( (uncake.Course)subject ).code == ( (uncake.Course)subject2 ).code ){
                        if( ( (uncake.Course)subject ).grade < ( (uncake.Course)subject2 ).grade )
                            subjectsPA.remove(i)
                        else
                            subjectsPA.remove(j)
                    }
                }

            }
        }

        periods.eachWithIndex{ period, i ->
            gradesPAPAPerPeriod.add(0)
            creditsPAPAPerPeriod.add(0)
            gradesPAPerPeriod.add(0)
            creditsPAPerPeriod.add(0)
            subjectsPAPA.each{
                if( i + 1 == ( (uncake.Course)it ).semesterNumber ) {
                    gradesPAPAPerPeriod[i] += ( (uncake.Course)it ).grade * ( (uncake.Course)it ).credits
                    creditsPAPAPerPeriod[i] += ( (uncake.Course)it ).credits
                }
            }
            subjectsPA.each{
                if( i + 1 == ( (uncake.Course)it ).semesterNumber ) {
                    gradesPAPerPeriod[i] += ( (uncake.Course)it ).grade * ( (uncake.Course)it ).credits
                    creditsPAPerPeriod[i] += ( (uncake.Course)it ).credits
                }
            }
            gradesPAPASoFar += gradesPAPAPerPeriod[i]
            creditsPAPASoFar += creditsPAPAPerPeriod[i]
            PAPAPerPeriod.add( gradesPAPASoFar/creditsPAPASoFar )

            gradesPASoFar += gradesPAPerPeriod[i]
            creditsPASoFar += creditsPAPerPeriod[i]
            PAPerPeriod.add( gradesPASoFar / creditsPASoFar )
        }

        subjectsPA.each{
            if( ( (uncake.Course)it ).grade >= 3  ){
                if( ( (uncake.Course)it ).typology.equals("Fundamentación") )
                    creditsFundamentals += ( (uncake.Course)it ).credits
                if( ( (uncake.Course)it ).typology.equals("Disciplinar") )
                    creditsDisciplinary += ( (uncake.Course)it ).credits
                if( ( (uncake.Course)it ).typology.equals("Electiva") )
                    creditsFreeChoice += ( (uncake.Course)it ).credits
            }
        }

        subjectsPAPA.sort{ ((uncake.Course)it).semester }
        subjectsPAPA.each{
            def sub = (uncake.Course)it
            subjectsToPrint.add( String.valueOf( sub.code + '\\t' + sub.name + '\\t\\t\\t\\t\\t' + sub.credits + '\\t\\t\\t' + sub.grade ) )
        }

        def plan = ( (uncake.AcademicRecord)academicRecordToShow ).studyPlan
        def advance = ( (uncake.AcademicRecord)academicRecordToShow ).credits * 100 / ( ( (uncake.StudyPlan)plan ).fundamentalCredits + ( (uncake.StudyPlan)plan ).disciplinaryCredits + ( (uncake.StudyPlan)plan ).freeChoiceCredits )

        advanceComponents.add( ( (uncake.StudyPlan)plan ).fundamentalCredits )
        advanceComponents.add( creditsFundamentals )
        advanceComponents.add( ( (uncake.StudyPlan)plan ).disciplinaryCredits )
        advanceComponents.add( creditsDisciplinary )
        advanceComponents.add( ( (uncake.StudyPlan)plan ).freeChoiceCredits )
        advanceComponents.add( creditsFreeChoice )
        advanceComponents.add( ( (uncake.StudyPlan)plan ).languageCredits )
        advanceComponents.add( creditsLanguage )

        def data = PAPAPerPeriod + "&&&" + PAPerPeriod + "&&&" + periods + "&&&"
        data += subjectsToPrint + "&&&" + advanceComponents + "&&&" + advance + "&&&" + selectedRecord
        render data
    }

    def getAdvance = { advanceComponents ->
        if( advanceComponents.size() < 6 )
            return 0.0
        def advComp = []
        advanceComponents.each{ advComp.add( Integer.parseInt( String.valueOf(it) ) ) }
        def creditsApproved = advComp[1] + advComp[3] + advComp[5]
        def creditsRequired = advComp[0] + advComp[2] + advComp[4]
        return creditsRequired > 0 ? creditsApproved * 100.0 / creditsRequired : 0.0
    }

    def getAdvanceComponents = { academicRecord ->
        def advanceComponents = []
        def required = String.valueOf( academicRecord.find( requiredPattern ) )
        def approved = String.valueOf( academicRecord.find( approvedPattern ) )
        def positions = [ ADVANCE_FUND, ADVANCE_DISC, ADVANCE_FREE, ADVANCE_NIV ]
        positions.each{
            advanceComponents.add( Integer.parseInt( required.split('\t')[it] ) )
            advanceComponents.add( Integer.parseInt( approved.split('\t')[it] ) )
        }
        return advanceComponents
    }

    def getPAPA = { periods ->
        def papa = []
        def sumGrades = 0.0
        def sumCredits = 0.0
        periods.each{
            def periodsText = String.valueOf( it )
            while( periodsText.find( subjectPattern ) ){
                def subject = String.valueOf( periodsText.find( subjectPattern ) )
                def credits = Integer.parseInt( subject.split('\t')[SUBJECT_CREDITS] )
                def grade = Double.parseDouble( subject.split('\t')[SUBJECT_GRADE] )
                sumGrades += grade * credits
                sumCredits += credits
                periodsText = periodsText.replace( subject, "" )
            }
            papa.add( sumCredits > 0 ? sumGrades / sumCredits : 0.0 )
        }
        return papa
    }

    def getSumCredits = { periods ->
        def sumCredits = 0.0
        periods.each{
            def periodsText = String.valueOf( it )
            while( periodsText.find( subjectPattern ) ){
                def subject = String.valueOf( periodsText.find( subjectPattern ) )
                def credits = Integer.parseInt( subject.split('\t')[SUBJECT_CREDITS] )
                def grade = Double.parseDouble( subject.split('\t')[SUBJECT_GRADE] )
                if( grade >= 3 )
                    sumCredits += credits
                periodsText = periodsText.replace( subject, "" )
            }
        }
        return sumCredits
    }

    def getPA = { periods ->
        def pa = []
        def subjects = []
        def duplicatedSubject
        periods.eachWithIndex{ period, i ->
            def periodsText = String.valueOf( period )
            while( periodsText.find( subjectPattern ) ){
                def subjectText = String.valueOf( periodsText.find( subjectPattern ) )
                subjects.add( subjectText )
                periodsText = periodsText.replace( subjectText, "" )
            }
            def subjectsPA = []
            subjects.eachWithIndex{ subject, j ->
                subject = String.valueOf( subject )
                duplicatedSubject = false
                def code1 = Integer.parseInt( ( subject.split('\t')[SUBJECT_CODE] )[0..(subject.indexOf('-')-1)] )
                def grade1 = Double.parseDouble( subject.split('\t')[SUBJECT_GRADE] )
                subjects.eachWithIndex{ subject2, k ->
                    subject2 = String.valueOf( subject2 )
                    if( j != k ){
                        def code2 = Integer.parseInt( ( subject2.split('\t')[SUBJECT_CODE] )[0..(subject2.indexOf('-')-1)] )
                        if( code1 == code2 ) {
                            duplicatedSubject = true
                            def grade2 = Double.parseDouble( subject2.split('\t')[SUBJECT_GRADE] )
                            if( grade2 >= grade1 ) {
                                if( !subjectsPA.contains( subject2 ) )
                                    subjectsPA.add( subject2 )
                                if( subjectsPA.contains( subject ) )
                                    subjectsPA.remove( subject )
                            }
                            else {
                                if( !subjectsPA.contains( subject ) )
                                    subjectsPA.add( subject )
                                if( subjectsPA.contains( subject2 ) )
                                    subjectsPA.remove( subject2 )
                            }
                        }
                    }
                }
                if(!duplicatedSubject)
                    subjectsPA.add( subject )
            }
            def sumGrades = 0.0;
            def sumCredits = 0.0;
            subjectsPA.each{
                def grade = Double.parseDouble( it.split('\t')[SUBJECT_GRADE] )
                def credits = Integer.parseInt( it.split('\t')[SUBJECT_CREDITS] )
                sumGrades += grade * credits
                sumCredits += credits
            }
            pa.add( sumCredits > 0 ? sumGrades / sumCredits : 0.0 )
        }
        return pa
    }

    def getPeriods = { academicRecord ->
        def periods = []
        def recordSoFar = academicRecord
        def periodName = recordSoFar.find( periodPattern )
        recordSoFar = recordSoFar.substring( recordSoFar.indexOf( periodName ) ).replace( periodName, "" )
        while( recordSoFar.find( periodPattern ) ) {
            periodName = recordSoFar.find( periodPattern )
            periods.add( recordSoFar.substring( 0, recordSoFar.indexOf( periodName ) ) )
            recordSoFar = recordSoFar.substring( recordSoFar.indexOf( periodName ) ).replace( periodName, "" )
        }
        periods.add( recordSoFar )
        return periods
    }

    def getPeriodNames = { academicRecord ->
        def periodNames = []
        def recordSoFar = academicRecord
        while( recordSoFar.find( periodPattern ) ) {
            def periodName = recordSoFar.find( periodPattern )
            periodNames.add( periodName.split("\\|")[1] )
            recordSoFar = recordSoFar.substring( recordSoFar.indexOf( periodName ) ).replace( periodName, "" )
        }
        return periodNames
    }

    def getSubjects = { periods ->
        def subjects = []
        periods.each{
            def periodsText = String.valueOf( it )
            while( periodsText.find( subjectPattern ) ){
                def subject = String.valueOf( periodsText.find( subjectPattern ) )
                subjects.add( subject )
                periodsText = periodsText.replace( subject, "" )
            }
        }
        return subjects
    }

    def getCoursesToSave = { periods, periodNames ->
        def coursesToSave = []
        def typologies = [ 'B' : 'Fundamentación', 'C' : 'Disciplinar', 'L' : 'Electiva', 'P' : 'Idioma y nivelación' ]
        periods.eachWithIndex{ it, i ->
            def periodsText = String.valueOf( it )
            while( periodsText.find( subjectPattern ) ){
                def subject = String.valueOf( periodsText.find( subjectPattern ) )
                def code = Integer.parseInt( ( subject.split('\t')[SUBJECT_CODE] )[0..(subject.indexOf('-')-1)] )
                def name = subject.split('\t')[SUBJECT_NAME]
                def credits = Integer.parseInt( subject.split('\t')[SUBJECT_CREDITS] )
                def grade = Double.parseDouble( subject.split('\t')[SUBJECT_GRADE] )
                def typology = typologies[ subject.split('\t')[SUBJECT_TYPOLOGY] ]

                def newCourse = new uncake.Course( code: code, name: name, typology: typology, credits: credits, grade: grade, semester: String.valueOf( periodNames[i] ), semesterNumber: i + 1 )
                coursesToSave.add( newCourse )
                periodsText = periodsText.replace( subject, "" )
            }
            while( periodsText.find( subjectLevelPattern ) ){
                def subject = String.valueOf( periodsText.find( subjectLevelPattern ) )
                def code = Integer.parseInt( ( subject.split('\t')[SUBJECT_CODE] )[0..(subject.indexOf('-')-1)] )
                def name = subject.split('\t')[SUBJECT_NAME]
                def credits = Integer.parseInt( subject.split('\t')[SUBJECT_CREDITS] )
                def typology = typologies[ subject.split('\t')[SUBJECT_TYPOLOGY] ]
                def newCourse = new uncake.Course( code: code, name: name, typology: typology, credits: credits, grade: 5, semester: String.valueOf( periodNames[i] ), semesterNumber: i + 1 )
                coursesToSave.add( newCourse )
                periodsText = periodsText.replace( subject, "" )
            }
        }
        return coursesToSave
    }

}

